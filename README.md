# Hotel Booking

Учебная система бронирования отелей на Spring Boot. Проект состоит из четырёх приложений в одной Maven-сборке.

| Модуль | Ответственность |
| --- | --- |
| `hotel-service` | Каталог отелей, номера и занятость по датам |
| `booking-service` | Пользователи и бронирования |
| `eureka-server` | Регистрация и обнаружение сервисов |
| `api-gateway` | Входящие запросы и маршрутизация |

## Сборка

Требуется JDK 21. Maven Wrapper загрузит Maven 3.9.11 и зависимости при первой сборке. Если на macOS установлено несколько JDK, выберите нужный перед запуском.

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
./mvnw clean verify
```

На Windows укажите JDK 21 в `JAVA_HOME` и выполните `mvnw.cmd clean verify`. На Linux с выбранным JDK 21 выполните `./mvnw clean verify`.

Команда собирает отдельный запускаемый JAR в `target/` каждого модуля и проверяет формат Java через Spotless. Чтобы применить форматирование, запустите `./mvnw spotless:apply`.

## Запуск Hotel Service

```sh
"$JAVA_HOME/bin/java" -jar hotel-service/target/hotel-service-0.1.0-SNAPSHOT.jar
```

По умолчанию Hotel Service использует профиль `local` и слушает `127.0.0.1:8082`. Из другого терминала проверьте состояние сервиса.

```sh
curl -i http://127.0.0.1:8082/actuator/health
```

Ожидаемый ответ — HTTP 200 и `{"status":"UP"}`. Чтобы остановить сервис, нажмите Ctrl+C в терминале, где запущен Java-процесс.

Тест Hotel Service работает с профилем `test`. Его можно запустить отдельно командой `./mvnw -pl hotel-service test`.

### Доступ к Hotel

Hotel проверяет JWT от Booking самостоятельно, в том числе при обращении в обход Gateway. Ему нужен открытый ключ Booking: по умолчанию `secrets/booking-public.pem` из корня репозитория, другой путь задаёт `HOTEL_JWT_PUBLIC_KEY=file:/путь/к/ключу.pem`. Hotel проверяет подпись RS256, срок, издателя (`HOTEL_JWT_ISSUER`, по умолчанию `http://booking-service`) и аудиторию.

Для пользовательского токена Hotel на каждом защищённом запросе спрашивает Booking, не отозван ли токен (`GET /internal/auth/validate`). Адрес Booking задаёт `HOTEL_BOOKING_URL`, по умолчанию `http://127.0.0.1:8081`. Подключение ограничено 500 мс, ожидание ответа — 1 с. Поэтому Booking должен быть запущен.

| Запрос | Доступ |
| --- | --- |
| `GET /actuator/health` | Открыт |
| `GET /api/hotels`, `GET /api/hotels/{id}`, `GET /api/rooms/{id}` | `USER`, `ADMIN` |
| `POST`, `PATCH`, `DELETE` отелей и номеров, `GET /api/rooms/catalog` | `ADMIN` |
| `POST /api/rooms/{id}/confirm-availability`, `/release` | Только служебный токен Booking |

Без токена или с недействительным токеном ответ 401, с недостаточной ролью — 403. Если пользователя удалили или сменили ему роль либо пароль, старый токен сразу получает 401 и в Hotel. Если Booking недоступен, Hotel отвечает 503 `AUTH_SERVICE_UNAVAILABLE` и запрос не выполняет.

Для примеров ниже получите токен администратора через Booking (`POST /user/auth`) и сохраните его в переменной `ADMIN_TOKEN`.

## Каталог отелей

```sh
curl -i http://127.0.0.1:8082/api/hotels \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Отель Маяк","address":"ул. Морская, 7"}'
```

При создании API возвращает HTTP 201, сгенерированный `id` в JSON и путь в заголовке `Location`. Для чтения отправьте GET по этому пути, например `curl -i http://127.0.0.1:8082/api/hotels/1`.

Название и адрес обязательны, каждое поле ограничено 255 символами. Отели хранятся в памяти H2, поэтому после перезапуска Hotel Service каталог снова пуст.

Список `GET /api/hotels` возвращает страницу отелей в порядке возрастания `id`. Фильтры `name` и `address` ищут часть названия и адреса без учёта регистра. По пути из `Location` можно изменить адрес и удалить отель.

```sh
curl -i -X PATCH http://127.0.0.1:8082/api/hotels/1 \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"address":"ул. Морская, 9"}'
curl -i -X DELETE http://127.0.0.1:8082/api/hotels/1 \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

PATCH меняет переданные поля и возвращает отель с кодом 200. Пропущенные поля сохраняют прежние значения. Пустой объект, `null` вместо значения, пустые строки, неизвестные поля и значения других типов дают 400. Успешный DELETE возвращает 204. Повторный DELETE по тому же адресу возвращает 404.

Для ошибок каталога ответ содержит `code`, `message`, `timestamp` и `path`. При ошибках валидации полей есть также `fieldErrors`.

## Каталог номеров

Чтобы создать номер, укажите `id` существующего отеля и строковое обозначение номера.

```sh
curl -i http://127.0.0.1:8082/api/rooms \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"hotelId":1,"number":"010A"}'
```

POST возвращает 201 и путь карточки в `Location`, например `/api/rooms/1`. GET по этому пути возвращает номер, а `GET /api/rooms/catalog` возвращает страницу каталога по возрастанию `id`. Фильтры каталога — `hotelId` и `available=true` или `available=false`. Номер хранится строкой до 64 символов с удалением пробелов по краям. Повтор обозначения в том же отеле даёт 409 с `ROOM_NUMBER_CONFLICT`.

Оба списка принимают `page` с нуля и `size` от 1 до 100. По умолчанию `page=0&size=20`. Смещение больше 2 147 483 647 даёт 400. Ответ содержит массив записей, а заголовок `X-Total-Count` показывает количество записей после применения фильтров.

Новый номер получает `available: true` и `timesBooked: 0`. `available` показывает состояние эксплуатации. Каталог служит для управления номерами. Поиск свободных номеров на даты будет отдельной операцией.

```sh
curl -i -X PATCH http://127.0.0.1:8082/api/rooms/1 \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"number":"010B","available":false}'
curl -i -X DELETE http://127.0.0.1:8082/api/rooms/1 \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

Строгий PATCH принимает `number` и `available`. Отель закрепляется при создании номера. DELETE номера возвращает 204 после удаления и 404 для отсутствующей записи. Удаление отеля со связанными номерами возвращает 409.

Номер нельзя перевести в `available=false`, пока у него есть текущее или будущее проживание: ответ 409 `ROOM_HAS_ACTIVE_ALLOCATIONS`. Номер с любой историей выделений нельзя удалить: ответ 409 `ROOM_HAS_ALLOCATION_HISTORY`, вместо удаления его отключают. Так сохраняются история и статистика.

## Поиск свободных номеров и рекомендации

Даты — это интервал `[startDate, endDate)` в формате `YYYY-MM-DD`: в день выезда номер уже свободен для следующего гостя. Заезд не раньше сегодняшней даты по Москве и не дальше 365 дней, выезд строго позже заезда, не больше 30 ночей. Нарушение даёт 400 `VALIDATION_FAILED` с полем в `fieldErrors`.

```sh
curl -i 'http://127.0.0.1:8082/api/rooms?startDate=2026-12-10&endDate=2026-12-12&hotelId=1' \
  -H "Authorization: Bearer $USER_TOKEN"
curl -i 'http://127.0.0.1:8082/api/rooms/recommend?startDate=2026-12-10&endDate=2026-12-12' \
  -H "Authorization: Bearer $USER_TOKEN"
```

Оба запроса доступны USER и ADMIN и используют один фильтр: номер в эксплуатации (`available=true`) и без активного выделения, пересекающего даты. `GET /api/rooms` сортирует по `id`, `GET /api/rooms/recommend` — по возрастанию `timesBooked`, при равенстве по `id`. Параметры `page` и `size` работают как в каталоге.

`GET /api/rooms/statistics?hotelId=1` (ADMIN) показывает для каждого номера `timesBooked` и `activeAllocations` — число текущих и будущих проживаний.

## Служебные шаги бронирования

`POST /api/rooms/{id}/confirm-availability` и `POST /api/rooms/{id}/release` вызывает только Booking со служебным токеном. Тело обоих запросов одинаковое.

```json
{"bookingId":7,"allocationId":"booking-7-room-1","startDate":"2026-12-10","endDate":"2026-12-12"}
```

Hotel хранит выделения в таблице `room_allocations` в состояниях `ACTIVE`, `REJECTED` и `RELEASED`. Обе операции блокируют строку номера, поэтому одновременные запросы обрабатываются по очереди.

| Ситуация | Ответ |
| --- | --- |
| Даты свободны | 200, `ACTIVE`, `timesBooked` +1 |
| Повтор того же `allocationId` с теми же данными | Прежний результат, счётчик не меняется |
| Даты заняты или номер отключён | 409 `ROOM_UNAVAILABLE`, попытка сохраняется как `REJECTED` |
| Тот же `allocationId` с другими данными | 409 `ALLOCATION_MISMATCH` |
| release активного выделения | 200, `RELEASED`, `timesBooked` −1 |
| Повторный release | 200, без изменений |
| release раньше confirm | 200, `RELEASED`. Поздний confirm получает 409 `ALLOCATION_RELEASED` |

## Запуск Booking Service

Booking подписывает токены локальным закрытым ключом RSA и проверяет их открытым ключом. В чистой копии проекта создайте пару один раз из корня репозитория. Папка `secrets/` исключена из Git.

```sh
umask 077
mkdir -p secrets
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out secrets/booking-private.pem
openssl pkey -in secrets/booking-private.pem -pubout -out secrets/booking-public.pem
```

Для других путей укажите `BOOKING_JWT_PRIVATE_KEY` и `BOOKING_JWT_PUBLIC_KEY` в формате `file:/путь/к/ключу.pem`. При необходимости `BOOKING_JWT_ISSUER` задаёт издателя токена, по умолчанию это `http://booking-service`.

Первый администратор создаётся при старте из переменных окружения, до того как Booking начинает принимать запросы. Пароль подчиняется тем же правилам, что и при регистрации. Если переменные не заданы, сервис стартует без ADMIN и пишет предупреждение в лог. Повторный запуск с существующим логином ничего не меняет.

```sh
BOOKING_ADMIN_USERNAME=admin BOOKING_ADMIN_PASSWORD='<пароль от 12 символов>' \
  "$JAVA_HOME/bin/java" -jar booking-service/target/booking-service-0.1.0-SNAPSHOT.jar
```

По умолчанию Booking слушает `127.0.0.1:8081` и использует отдельную H2 `jdbc:h2:mem:booking`. Проверьте запуск командой `curl -i http://127.0.0.1:8081/actuator/health`.

Учётные записи хранятся в таблице `users`. Логин приводится к нижнему регистру с удалением пробелов по краям, максимальная длина — 64 символа. Пароль содержит от 12 до 128 символов. Сервис записывает хеш PBKDF2 со случайной солью, а DTO пользователя содержит `id`, `username` и `role`. Хранение можно проверить командой `./mvnw -pl booking-service test`.

Для регистрации и входа отправьте тестовые учётные данные в локальный Booking.

```sh
curl -i http://127.0.0.1:8081/user/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"NewGuest","password":"пример-пароля-42"}'
curl -i http://127.0.0.1:8081/user/auth \
  -H 'Content-Type: application/json' \
  -d '{"username":"NEWGUEST","password":"пример-пароля-42"}'
```

Регистрация возвращает 201 и назначает роль `USER`. Вход с правильным паролем возвращает 200. Успешные ответы содержат `id`, `username`, `role`, `accessToken`, `tokenType` и `expiresIn`. Токен подписан RS256 и действует 3600 секунд. Неверные учётные данные дают 401, повтор логина даёт 409, ошибки ввода дают 400.

Для локальной проверки подставьте полученный `accessToken`. Внутренняя проверка Booking отвечает 204 для действующего токена и 401 для отсутствующего, недействительного или отозванного.

```sh
curl -i http://127.0.0.1:8081/internal/auth/validate \
  -H 'Authorization: Bearer <accessToken>'
```

Booking проверяет подпись, срок, издателя, аудиторию `hotel-booking-api` и версию доступа в H2 при каждом защищённом запросе.

### Администрирование пользователей

Все операции требуют JWT с ролью `ADMIN`. Без токена ответ 401, с ролью `USER` — 403. Пути используют идентификатор в адресе. Примеры ТЗ `PATCH /user` и `DELETE /user` без id заменены на REST-форму осознанно.

| Метод и путь | Тело | Ответ |
| --- | --- | --- |
| `POST /user` | `username`, `password`, `role` (`USER` или `ADMIN`) | 201, `Location: /user/{id}` |
| `GET /users?page=0&size=20` | — | 200, заголовок `X-Total-Count`, сортировка по `id`, `size` до 100 |
| `GET /user/{id}` | — | 200 или 404 `USER_NOT_FOUND` |
| `PATCH /user/{id}` | Одно или несколько полей `username`, `password`, `role` | 200 |
| `DELETE /user/{id}` | — | 204, 404, 409 |

```sh
curl -i http://127.0.0.1:8081/user \
  -H 'Authorization: Bearer <adminToken>' -H 'Content-Type: application/json' \
  -d '{"username":"guest-one","password":"guest-password-1","role":"USER"}'
curl -i -X PATCH http://127.0.0.1:8081/user/2 \
  -H 'Authorization: Bearer <adminToken>' -H 'Content-Type: application/json' \
  -d '{"role":"ADMIN"}'
```

Смена роли или пароля сразу отзывает выданные пользователю токены: следующий запрос со старым JWT получает 401. Смена логина токены не отзывает. Удаление пользователя, у которого есть хотя бы одна бронь в любом статусе, даёт 409 `USER_HAS_BOOKINGS`. Удаление или понижение последнего администратора даёт 409 `LAST_ADMIN`.

### Свои бронирования

Доступны ролям `USER` и `ADMIN`. Владелец определяется по `sub` из токена, параметр `userId` в запросе не учитывается. Создание и отмена броней появятся вместе с интеграцией Hotel.

| Метод и путь | Ответ |
| --- | --- |
| `GET /bookings?page=0&size=20` | 200, свои брони от новых к старым (`created_at DESC, id DESC`), заголовок `X-Total-Count` |
| `GET /booking/{id}` | 200 со своей бронью. Чужая или отсутствующая даёт 404 `BOOKING_NOT_FOUND` |

Таблица `bookings` содержит `id`, `user_id`, `room_id`, `start_date`, `end_date`, `status` (`PENDING`, `CONFIRMED`, `CANCELLED`) и `created_at`. Внешний ключ связывает `user_id` с `users`, ограничение запрещает `end_date <= start_date`, индекс `(user_id, created_at, id)` обслуживает историю.
