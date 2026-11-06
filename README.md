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

## Каталог отелей

```sh
curl -i http://127.0.0.1:8082/api/hotels \
  -H 'Content-Type: application/json' \
  -d '{"name":"Отель Маяк","address":"ул. Морская, 7"}'
```

При создании API возвращает HTTP 201, сгенерированный `id` в JSON и путь в заголовке `Location`. Для чтения отправьте GET по этому пути, например `curl -i http://127.0.0.1:8082/api/hotels/1`.

Название и адрес обязательны, каждое поле ограничено 255 символами. Отели хранятся в памяти H2, поэтому после перезапуска Hotel Service каталог снова пуст.

Список `GET /api/hotels` возвращает страницу отелей в порядке возрастания `id`. Фильтры `name` и `address` ищут часть названия и адреса без учёта регистра. По пути из `Location` можно изменить адрес и удалить отель.

```sh
curl -i -X PATCH http://127.0.0.1:8082/api/hotels/1 \
  -H 'Content-Type: application/json' \
  -d '{"address":"ул. Морская, 9"}'
curl -i -X DELETE http://127.0.0.1:8082/api/hotels/1
```

PATCH меняет переданные поля и возвращает отель с кодом 200. Пропущенные поля сохраняют прежние значения. Пустой объект, `null` вместо значения, пустые строки, неизвестные поля и значения других типов дают 400. Успешный DELETE возвращает 204. Повторный DELETE по тому же адресу возвращает 404.

Для ошибок каталога ответ содержит `code`, `message`, `timestamp` и `path`. При ошибках валидации полей есть также `fieldErrors`.

## Каталог номеров

Чтобы создать номер, укажите `id` существующего отеля и строковое обозначение номера.

```sh
curl -i http://127.0.0.1:8082/api/rooms \
  -H 'Content-Type: application/json' \
  -d '{"hotelId":1,"number":"010A"}'
```

POST возвращает 201 и путь карточки в `Location`, например `/api/rooms/1`. GET по этому пути возвращает номер, а `GET /api/rooms/catalog` возвращает страницу каталога по возрастанию `id`. Фильтры каталога — `hotelId` и `available=true` или `available=false`. Номер хранится строкой до 64 символов с удалением пробелов по краям. Повтор обозначения в том же отеле даёт 409 с `ROOM_NUMBER_CONFLICT`.

Оба списка принимают `page` с нуля и `size` от 1 до 100. По умолчанию `page=0&size=20`. Смещение больше 2 147 483 647 даёт 400. Ответ содержит массив записей, а заголовок `X-Total-Count` показывает количество записей после применения фильтров.

Новый номер получает `available: true` и `timesBooked: 0`. `available` показывает состояние эксплуатации. Каталог служит для управления номерами. Поиск свободных номеров на даты будет отдельной операцией.

```sh
curl -i -X PATCH http://127.0.0.1:8082/api/rooms/1 \
  -H 'Content-Type: application/json' \
  -d '{"number":"010B","available":false}'
curl -i -X DELETE http://127.0.0.1:8082/api/rooms/1
```

Строгий PATCH принимает `number` и `available`. Отель закрепляется при создании номера. DELETE номера возвращает 204 после удаления и 404 для отсутствующей записи. Удаление отеля со связанными номерами возвращает 409. Проверку действующих броней при отключении и удалении добавим вместе с календарём занятости.

## Запуск Booking Service

Booking подписывает токены локальным закрытым ключом RSA и проверяет их открытым ключом. В чистой копии проекта создайте пару один раз из корня репозитория. Папка `secrets/` исключена из Git.

```sh
umask 077
mkdir -p secrets
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out secrets/booking-private.pem
openssl pkey -in secrets/booking-private.pem -pubout -out secrets/booking-public.pem
```

Для других путей укажите `BOOKING_JWT_PRIVATE_KEY` и `BOOKING_JWT_PUBLIC_KEY` в формате `file:/путь/к/ключу.pem`. При необходимости `BOOKING_JWT_ISSUER` задаёт издателя токена, по умолчанию это `http://booking-service`.

```sh
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
