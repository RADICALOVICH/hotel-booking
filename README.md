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

## Запуск каркаса Hotel Service

```sh
"$JAVA_HOME/bin/java" -jar hotel-service/target/hotel-service-0.1.0-SNAPSHOT.jar
```

Пока приложения содержат точки входа Spring Boot. В этом примере появится строка `Started HotelServiceApplication`, после чего процесс завершится.
