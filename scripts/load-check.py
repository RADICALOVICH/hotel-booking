#!/usr/bin/env python3
"""Run the A03 load scenarios through four packaged Spring Boot services."""

import datetime as dt
import json
import os
import secrets
import signal
import socket
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor
from functools import partial
from pathlib import Path
from zoneinfo import ZoneInfo


ROOT = Path(__file__).resolve().parents[1]
BOOKING = "/api/bookings/booking"
HISTORY = "/api/bookings/bookings"
TODAY = dt.datetime.now(ZoneInfo("Europe/Moscow")).date()
HTTP = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def request(base, method, path, expected=None, *, token=None, body=None):
    headers = {"Accept": "application/json"}
    data = None
    if token:
        headers["Authorization"] = "Bearer " + token
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode()
    command = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    try:
        response = HTTP.open(command, timeout=80)
    except urllib.error.HTTPError as failure:
        response = failure
    with response:
        raw = response.read()
        try:
            payload = json.loads(raw) if raw else None
        except ValueError:
            payload = None
        if expected is not None and response.status != expected:
            code = payload.get("code") if isinstance(payload, dict) else None
            raise AssertionError(f"{method} {path} → {response.status} {code}, expected {expected}")
        return response.status, response.headers, payload


def wait_for(description, check):
    deadline = time.monotonic() + 60
    last_error = None
    while time.monotonic() < deadline:
        try:
            result = check()
            if result:
                return result
        except (OSError, AssertionError) as error:
            last_error = error
        time.sleep(0.25)
    raise RuntimeError(f"Timed out waiting for {description}") from last_error


def free_ports(count):
    listeners = [socket.socket() for _ in range(count)]
    try:
        for listener in listeners:
            listener.bind(("127.0.0.1", 0))
        return [listener.getsockname()[1] for listener in listeners]
    finally:
        for listener in listeners:
            listener.close()


def boot_services(processes, logs, key_dir):
    private = key_dir / "private.pem"
    public = key_dir / "public.pem"
    subprocess.run(["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048",
                    "-out", str(private)], check=True, capture_output=True)
    subprocess.run(["openssl", "pkey", "-in", str(private), "-pubout", "-out", str(public)],
                   check=True, capture_output=True)
    modules = ("eureka-server", "booking-service", "hotel-service", "api-gateway")
    ports = dict(zip(modules, free_ports(len(modules))))
    admin_password = secrets.token_urlsafe(24)
    application_prefixes = ("SPRING_", "SERVER_", "MANAGEMENT_", "EUREKA_", "BOOKING_", "HOTEL_", "GATEWAY_")
    java_options = {"JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"}
    env = {name: value for name, value in os.environ.items()
           if not name.startswith(application_prefixes) and name not in java_options}
    env.update({
        "OCCUPANCY_SHARED_SECRET": secrets.token_hex(32),
        "EUREKA_USERNAME": "load-registry",
        "EUREKA_PASSWORD": secrets.token_urlsafe(24),
        "EUREKA_URL": f"http://127.0.0.1:{ports['eureka-server']}/eureka/",
        "BOOKING_ADMIN_USERNAME": "load-admin",
        "BOOKING_ADMIN_PASSWORD": admin_password,
        "BOOKING_JWT_PRIVATE_KEY": private.as_uri(),
        "BOOKING_JWT_PUBLIC_KEY": public.as_uri(),
        "HOTEL_JWT_PUBLIC_KEY": public.as_uri(),
        "GATEWAY_JWT_PUBLIC_KEY": public.as_uri(),
    })
    java_home = os.environ.get("JAVA_HOME")
    java = str(Path(java_home) / "bin/java") if java_home else "java"

    def start(module):
        port = ports[module]
        jar = ROOT / module / "target" / f"{module}-0.1.0-SNAPSHOT.jar"
        if not jar.is_file():
            raise FileNotFoundError(f"Build the reactor before running the load check. Missing {jar}")
        with (logs / f"{module}.log").open("wb") as stream:
            process = subprocess.Popen([java, "-jar", str(jar), f"--server.port={port}"],
                                       cwd=ROOT, env=env, stdout=stream, stderr=subprocess.STDOUT)
        processes.append(process)
        return process

    def healthy(module, process):
        if process.poll() is not None:
            raise RuntimeError(f"{module} exited with {process.returncode}")
        base = f"http://127.0.0.1:{ports[module]}"
        _, _, payload = request(base, "GET", "/actuator/health", 200)
        return payload is not None and payload.get("status") == "UP"

    registry = start(modules[0])
    wait_for("healthy Eureka", lambda: healthy(modules[0], registry))
    clients = {module: start(module) for module in modules[1:]}
    for module, process in clients.items():
        wait_for(f"healthy {module}", lambda: healthy(module, process))
    call = partial(request, f"http://127.0.0.1:{ports['api-gateway']}")
    credentials = {"username": "load-admin", "password": admin_password}

    def get_admin_token():
        return call("POST", "/api/bookings/user/auth", 200, body=credentials)[2]["accessToken"]

    admin = wait_for("Gateway routing to Booking", get_admin_token)
    wait_for("Gateway routing to Hotel", lambda: call("GET", "/api/hotels", 200, token=admin))
    return call, admin


def stop(processes):
    for process in reversed(processes):
        if process.poll() is None:
            process.terminate()
    for process in reversed(processes):
        try:
            process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()


def dates(offset, nights=1):
    start = TODAY + dt.timedelta(days=offset)
    return {"startDate": start.isoformat(), "endDate": (start + dt.timedelta(days=nights)).isoformat()}


def booking(dates_, *, room_id=None, hotel_id=None, request_id=None):
    body = dict(dates_, requestId=request_id or str(uuid.uuid4()), autoSelect=room_id is None)
    if room_id is None:
        body["hotelId"] = hotel_id
    else:
        body["roomId"] = room_id
    return body


def catalog(call, admin, hotel_id):
    path = f"/api/rooms/catalog?hotelId={hotel_id}&size=100"
    return call("GET", path, 200, token=admin)[2]


def hotel_with_rooms(call, admin, name, count):
    hotel_id = call("POST", "/api/hotels", 201, token=admin,
                    body={"name": name, "address": "A03 Load Check Street"})[2]["id"]
    room_ids = [call("POST", "/api/rooms", 201, token=admin,
                     body={"hotelId": hotel_id, "number": str(index + 1)})[2]["id"]
                for index in range(count)]
    assert {row["id"]: row["timesBooked"] for row in catalog(call, admin, hotel_id)} == dict.fromkeys(room_ids, 0)
    return hotel_id, room_ids


def race(call, submissions):
    barrier = threading.Barrier(len(submissions) + 1, timeout=15)

    def submit(token, body):
        barrier.wait()
        began = time.monotonic()
        status, _, payload = call("POST", BOOKING, token=token, body=body)
        return token, status, payload, time.monotonic() - began

    with ThreadPoolExecutor(max_workers=len(submissions)) as workers:
        jobs = [workers.submit(submit, token, body) for token, body in submissions]
        began = time.monotonic()
        barrier.wait()
        results = [job.result() for job in jobs]
    return results, time.monotonic() - began


def expect_race(results, statuses, conflict_code):
    actual = Counter(status for _, status, _, _ in results)
    codes = Counter(payload.get("code") if isinstance(payload, dict) else None
                    for _, status, payload, _ in results if status != 201)
    assert actual == statuses, f"Expected HTTP {statuses}, received {actual}, codes={codes}"
    assert codes == {conflict_code: statuses[409]}, codes


def history(call, token):
    rows = []
    for page in range(10):
        _, headers, batch = call("GET", f"{HISTORY}?page={page}&size=100", 200, token=token)
        rows.extend(batch)
        if len(rows) == int(headers["X-Total-Count"]):
            return rows
    raise AssertionError("History pagination did not return the reported number of rows")


def record_results(expected, results):
    ids = set()
    for token, status, payload, _ in results:
        booking_id = payload["id"] if status == 201 else payload["bookingId"]
        assert booking_id not in ids, "Distinct requests returned a duplicate booking ID"
        ids.add(booking_id)
        expected[token][booking_id] = "CONFIRMED" if status == 201 else "CANCELLED"


def verify_histories(call, admin, expected, rooms_by_hotel):
    by_room = defaultdict(list)
    seen_ids = set()
    for token, expected_by_id in expected.items():
        rows = history(call, token)
        actual = {row["id"]: row["status"] for row in rows}
        assert len(actual) == len(rows) and actual == expected_by_id, "Missing, foreign or duplicate history rows"
        assert seen_ids.isdisjoint(actual), "A booking appeared in multiple owners’ histories"
        seen_ids.update(actual)
        assert all(not row["compensationPending"] for row in rows), "Unexpected pending compensation"
        for row in rows:
            if row["status"] == "CONFIRMED":
                by_room[row["roomId"]].append((row["startDate"], row["endDate"]))

    assert by_room.keys() <= {room_id for room_ids in rooms_by_hotel.values() for room_id in room_ids}
    for room_id, intervals in by_room.items():
        ordered = sorted(intervals)
        assert all(left[1] <= right[0] for left, right in zip(ordered, ordered[1:])), f"Overlapping stays for room {room_id}"
    for hotel_id, room_ids in rooms_by_hotel.items():
        actual = {row["id"]: row["timesBooked"] for row in catalog(call, admin, hotel_id)}
        assert actual == {room_id: len(by_room[room_id]) for room_id in room_ids}, "Hotel counters disagree with Booking history"


def exercise(call, admin):
    guests = [call("POST", "/api/bookings/user/register", 201,
                   body={"username": f"load-guest-{index}", "password": secrets.token_urlsafe(24)})[2]["accessToken"]
              for index in range(2)]
    expected = {token: {} for token in [admin, *guests]}
    rooms_by_hotel = {}

    contention_hotel, rooms = hotel_with_rooms(call, admin, "Parallel Ten Rooms", 10)
    rooms_by_hotel[contention_hotel] = rooms
    same_dates = dates(2, nights=2)
    results, elapsed = race(call, [(guests[index % 2], booking(same_dates, hotel_id=contention_hotel))
                                   for index in range(50)])
    expect_race(results, {201: 10, 409: 40}, "NO_ROOMS_AVAILABLE")
    record_results(expected, results)
    allocated_rooms = {payload["roomId"] for _, status, payload, _ in results if status == 201}
    assert allocated_rooms == set(rooms), "A free room was skipped or a room was double-booked"
    query = urllib.parse.urlencode(dict(same_dates, hotelId=contention_hotel))
    assert call("GET", f"/api/rooms?{query}", 200, token=guests[0])[2] == []
    latencies = sorted(duration for _, _, _, duration in results)
    print(f"T57 parallel auto-select: 50 POST, 10×201, 40×409, {elapsed:.2f}s total, "
          f"p95={latencies[47]:.2f}s, max={latencies[-1]:.2f}s", flush=True)

    larger_hotel, larger_rooms = hotel_with_rooms(call, admin, "Parallel Thirty Rooms", 30)
    rooms_by_hotel[larger_hotel] = larger_rooms
    larger_dates = dates(8, nights=2)
    results, elapsed = race(call, [(guests[index % 2], booking(larger_dates, hotel_id=larger_hotel))
                                   for index in range(50)])
    expect_race(results, {201: 30, 409: 20}, "NO_ROOMS_AVAILABLE")
    record_results(expected, results)
    allocated = {payload["roomId"] for _, status, payload, _ in results if status == 201}
    assert allocated == set(larger_rooms), "Auto-selection stopped before visiting all free rooms"
    query = urllib.parse.urlencode(dict(larger_dates, hotelId=larger_hotel))
    assert call("GET", f"/api/rooms?{query}", 200, token=guests[0])[2] == []
    print(f"T57 larger catalog: 50 POST, 30×201, 20×409, {elapsed:.2f}s total; "
          "all thirty rooms allocated", flush=True)

    single_hotel, single_rooms = hotel_with_rooms(call, admin, "One Contended Room", 1)
    rooms_by_hotel[single_hotel] = single_rooms
    only_room = single_rooms[0]
    results, _ = race(call, [(guests[index % 2], booking(dates(4), room_id=only_room))
                             for index in range(8)])
    expect_race(results, {201: 1, 409: 7}, "ROOM_UNAVAILABLE")
    record_results(expected, results)

    repeated = booking(dates(5), room_id=only_room)
    results, _ = race(call, [(guests[0], repeated)] * 8)
    responses = Counter(status for _, status, _, _ in results)
    assert responses[201] == 1 and set(responses) <= {200, 201, 202}, responses
    booking_ids = {payload["id"] for _, _, payload, _ in results}
    assert len(booking_ids) == 1, "Repeated request allocated multiple bookings"
    id_ = booking_ids.pop()
    replayed = call("POST", BOOKING, 200, token=guests[0], body=repeated)[2]
    assert replayed["id"] == id_ and replayed["status"] == "CONFIRMED"
    expected[guests[0]][id_] = "CONFIRMED"
    changed = dict(repeated, **dates(6))
    assert call("POST", BOOKING, 409, token=guests[0], body=changed)[2]["code"] == "REQUEST_ID_CONFLICT"
    independent = call("POST", BOOKING, 201, token=admin, body=changed)[2]
    assert independent["id"] != id_ and independent["status"] == "CONFIRMED"
    expected[admin][independent["id"]] = "CONFIRMED"
    for other in (admin, guests[1]):
        assert call("GET", f"{BOOKING}/{id_}", 404, token=other)[2]["code"] == "BOOKING_NOT_FOUND"
    print(f"T28/T41/T42 single room: 1×201 + 7×409; repeated requestId: {dict(sorted(responses.items()))}, "
          "one booking; ADMIN has independent key scope and personal history", flush=True)

    sequential_hotel, sequential_rooms = hotel_with_rooms(call, admin, "Sequential Fairness", 10)
    rooms_by_hotel[sequential_hotel] = sequential_rooms
    counts = Counter(dict.fromkeys(sequential_rooms, 0))
    began = time.monotonic()
    for offset in range(20, 120):
        guest = guests[offset % 2]
        _, _, confirmed = call("POST", BOOKING, 201, token=guest,
                               body=booking(dates(offset), hotel_id=sequential_hotel))
        room_id = confirmed["roomId"]
        assert confirmed["status"] == "CONFIRMED"
        assert room_id == min(sequential_rooms, key=lambda room: (counts[room], room)), "Recommendation order changed"
        expected[guest][confirmed["id"]] = "CONFIRMED"
        counts[room_id] += 1
    spread = max(counts.values()) - min(counts.values())
    assert spread <= 1, counts
    print(f"T27 sequential auto-select: 100×201 in {time.monotonic() - began:.2f}s, "
          f"room counts={sorted(counts.values())}, spread={spread}", flush=True)

    verify_histories(call, admin, expected, rooms_by_hotel)
    print(f"T57 verified {sum(map(len, expected.values()))} history rows across two guests and ADMIN. "
          "No overlaps, duplicates or PENDING; Hotel counters agree.", flush=True)


def interrupt(_signal, _frame):
    raise KeyboardInterrupt


def main():
    if not __debug__:
        raise RuntimeError("Run the load check with Python assertions enabled")
    signal.signal(signal.SIGTERM, interrupt)
    logs = Path(tempfile.mkdtemp(prefix="hotel-load-check-"))
    processes = []
    with tempfile.TemporaryDirectory(prefix="hotel-load-keys-") as key_dir:
        try:
            call, admin = boot_services(processes, logs, Path(key_dir))
            exercise(call, admin)
        finally:
            stop(processes)
            print(f"Service logs: {logs}", flush=True)


if __name__ == "__main__":
    main()
