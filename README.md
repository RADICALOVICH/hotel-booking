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

## Создание и чтение отеля

```sh
curl -i http://127.0.0.1:8082/api/hotels \
  -H 'Content-Type: application/json' \
  -d '{"name":"Отель Маяк","address":"ул. Морская, 7"}'
```

При создании API возвращает HTTP 201, сгенерированный `id` в JSON и путь в заголовке `Location`. Для чтения отправьте GET по этому пути, например `curl -i http://127.0.0.1:8082/api/hotels/1`.

Название и адрес обязательны, каждое поле ограничено 255 символами. Отели хранятся в памяти H2, поэтому после перезапуска Hotel Service каталог снова пуст.
