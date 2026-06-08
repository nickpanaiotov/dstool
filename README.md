# DSTool

> **English summary.** DSTool is the missing **macOS** signing tool for **StampIT** and other
> Bulgarian qualified electronic signatures (QES). StampIT's own desktop signer — also called
> *DSTool* — and its middleware are **Windows-only**, leaving Mac users unable to sign documents
> with their smart card or USB token. This project fills that gap: a small, open-source
> command-line tool that produces **CAdES** signatures (both **attached** `.p7m` and **detached**
> `.p7s`) by talking to the card through its PKCS#11 driver. It ships as a **single
> self-contained native binary** (GraalVM native image) — no Java, no jars, nothing to install.
> The user interface and the rest of this document are in **Bulgarian**, the tool's audience.
> Built on the EU [DSS](https://github.com/esig/dss) library; licensed under Apache-2.0.

---

## Какво е DSTool

**DSTool** е инструмент за команден ред за **macOS**, който подписва документи с
**квалифициран електронен подпис (КЕП)** от **StampIT** (и съвместими български доставчици),
използвайки смарт карта или USB токен.

Произвежда **CAdES** подписи в два формата:

- **прикачен** (attached / *enveloping*, `.p7m`) — подписът обгръща и съдържа самия документ
  в един файл;
- **отделен** (detached, `.p7s`) — подписът е отделен файл, който стои до оригинала, без да го
  променя.

## Защо съществува

StampIT (управляван от **Информационно обслужване АД**) е български доставчик на квалифицирани
удостоверителни услуги по eIDAS. Официалните му инструменти за подписване — включително
настолното приложение, носещо името **DSTool** — и драйверите за картите се разпространяват
**само за Windows**. Потребителите на macOS, които притежават КЕП от StampIT, на практика
нямат официален начин да подпишат документ.

Този проект запълва празнината: открит код, който върши същата работа на macOS — подписване с
вашата карта/токен директно през стандартния **PKCS#11** интерфейс на драйвера, без нужда от
Windows или платени инструменти на трети страни.

## Инсталация

1. Изтеглете архива за вашия Mac от страницата **Releases**
   (`dstool-<версия>-macos-arm64`). Препоръчва се Apple Silicon; на Intel машини двоичният
   файл работи през Rosetta 2.
2. Проверете контролната сума:
   ```sh
   shasum -a 256 -c dstool-<версия>-macos-arm64.sha256
   ```
3. Премахнете атрибута за карантина (файлът не е нотаризиран от Apple) и при нужда го
   подпишете локално:
   ```sh
   xattr -dr com.apple.quarantine ./dstool-<версия>-macos-arm64
   # ако macOS все още твърди, че файлът е „повреден" (Apple Silicon):
   codesign -s - ./dstool-<версия>-macos-arm64
   ```
4. Направете го изпълним и (по желание) го сложете в PATH:
   ```sh
   chmod +x ./dstool-<версия>-macos-arm64
   sudo mv ./dstool-<версия>-macos-arm64 /usr/local/bin/dstool
   ```

> Двоичният файл е **самостоятелен** — не изисква инсталиран Java и не съдържа външни `.jar`
> файлове.

## Употреба

```sh
# 1) Преглед на слотовете/ключовете в картата
dstool list-keys

# 2) Прикачен подпис -> zayavlenie.p7m
dstool sign zayavlenie.pdf

# 3) Отделен подпис -> zayavlenie.p7s
dstool sign --format detached zayavlenie.pdf

# Изрично указване на слот, драйвер, ключ, алгоритъм и изходен файл
dstool sign --slot 1 --format detached \
            --pkcs11-lib /usr/local/lib/libIDPrimePKCS11.dylib \
            --key-index 0 --digest SHA512 \
            -o /tmp/podpis.p7s zayavlenie.pdf
```

### Избор на слот и квалифициран сертификат

Някои български КЕП карти излагат **няколко слота с отделни PIN-ове**. Кой слот съдържа
квалифицирания подпис зависи от картата и от middleware-а — затова **не разчитайте на
етикета на слота**, а на **употребата на ключа**: квалифицираният сертификат е този с
ключова употреба **non-repudiation**. `list-keys` го отбелязва:

```
$ dstool list-keys --slot 0
Намерени ключове:
  [0] CN=Иван Иванов,... (сериен номер: 3c2e...)  [квалифициран подпис — non-repudiation]
```

Подпишете със слота, в който се вижда този ключ:

```sh
dstool sign --slot 0 zayavlenie.pdf
```

Бележки:

- С **OpenSC** (`PKCS#15 emulated`) квалифицираният ключ обикновено се вижда на основния
  слот `[0]`, а отделният слот с „Digital Signature PIN" може да изглежда празен — това е
  ограничение на емулацията, не грешка на DSTool.
- Ако `list-keys` на даден слот не показва ключ с „non-repudiation", опитайте другия слот
  или официалния PKCS#11 драйвер на доставчика.
- Ако картата има само един слот, `--slot` не е нужен.

PIN кодът се въвежда **интерактивно** (без ехо). За неинтерактивна употреба (CI, скриптове)
може да се зададе чрез променливата на средата `DSTOOL_PIN`, така че да не попада в историята
на конзолата:

```sh
DSTOOL_PIN=123456 dstool sign --format detached doklad.xml
```

> **Важно за отделените подписи:** файлът `.p7s` **не съдържа** оригиналния документ. За да
> бъде проверен подписът по-късно, трябва да запазите и оригинала.

### Кодове на изход

| Код | Значение |
|-----|----------|
| `0` | Успех |
| `1` | Грешка при изпълнение/подписване (грешка в токена, неуспешен запис) |
| `2` | Грешен вход или употреба (липсващ файл, грешен индекс на ключ, липсващ PIN) |
| `3` | PKCS#11 драйверът не е намерен |

## PKCS#11 драйвери (middleware)

Ако не зададете `--pkcs11-lib`, DSTool търси драйвера на следните известни пътища (първият
наличен се използва). Списъкът е **отправна точка** — потвърдете точния път за вашата карта:

| Драйвер | Път |
|---------|-----|
| OpenSC | `/Library/OpenSC/lib/opensc-pkcs11.so`, `/opt/homebrew/lib/opensc-pkcs11.so`, `/usr/local/lib/opensc-pkcs11.so` |
| Thales/Gemalto IDPrime, SafeNet | `/usr/local/lib/libIDPrimePKCS11.dylib`, `/usr/local/lib/libeTPkcs11.dylib`, `/Library/Frameworks/eToken.framework/Versions/Current/libeToken.dylib` |
| Charismathics / CardOS | `/usr/local/lib/libcmP11.dylib` |
| Bit4id | `/Applications/PKIManager-bit4id.app/Contents/Resources/etc/libbit4xpki.dylib` |

## Изграждане от източник

Изисквания: **GraalVM CE 25** (за `native-image`) и **Xcode Command Line Tools**.

```sh
# JDK 25 / GraalVM CE (напр. чрез SDKMAN)
sdk install java 25-graalce
xcode-select --install     # ако още не е инсталиран

# Тестове на JVM (подписване със софтуерен PKCS#12 ключ)
./mvnw test

# Основен резултат: самостоятелен двоичен файл -> target/dstool
./mvnw -Pnative package

# Резервен вариант: jpackage пакет с вграден JRE (за двете архитектури)
./mvnw -Pjpackage package
```

Вижте [CONTRIBUTING.md](CONTRIBUTING.md) за подробности.

## Как работи (накратко)

DSTool заменя стандартния `SunPKCS11` доставчик на JDK с **собствен PKCS#11 конектор, изграден
върху Java FFM / Project Panama** (без JNI). Точно това позволява на инструмента да работи като
**native image** — където `SunPKCS11` се проваля заради JNI. CAdES структурата се изгражда от
библиотеката **EU DSS** + **Bouncy Castle**, а самото подписване (`C_Sign`) се извършва от
картата.

## Лиценз

DSTool е лицензиран под **Apache License 2.0** (вижте [LICENSE](LICENSE)).
Вградените компоненти на трети страни и съответствието с LGPL-2.1 за EU DSS са описани в
[THIRD-PARTY-LICENSES.md](THIRD-PARTY-LICENSES.md) и [COMPLIANCE.md](COMPLIANCE.md).
