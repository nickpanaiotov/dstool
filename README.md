# DSTool

[![CI](https://github.com/nickpanaiotov/dstool/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/nickpanaiotov/dstool/actions/workflows/ci.yml) [![Latest release](https://img.shields.io/github/v/release/nickpanaiotov/dstool)](https://github.com/nickpanaiotov/dstool/releases/latest) [![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

> The **CI** badge above covers the unit tests (`./mvnw test`), the native-image build, and the
> CLI smoke test, on **macOS arm64** and **Linux x86_64**.

> **English summary.** DSTool is the missing **macOS and Linux** signing tool for **StampIT** and
> other Bulgarian qualified electronic signatures (QES). StampIT's own desktop signer — also called
> *DSTool* — and its middleware are **Windows-only**, leaving Mac users unable to sign documents
> with their smart card or USB token. This project fills that gap: a small, open-source
> command-line tool that produces **CAdES** signatures (both **attached** `.p7m` and **detached**
> `.p7s`) by talking to the card through its PKCS#11 driver. It ships as a **single
> self-contained native binary** (GraalVM native image) — no Java, no jars, nothing to install.
> The user interface and the rest of this document are in **Bulgarian**, the tool's audience.
> Built on the EU [DSS](https://github.com/esig/dss) library; licensed under Apache-2.0.
>
> **Independent & unofficial** — not affiliated with Информационно обслужване АД / StampIT.
> Built largely with AI assistance; provided **as is**, without warranty, use at your own risk.
> **Makes no network calls** — no inbound or outbound network activity whatsoever.

---

## ⚠️ Отказ от отговорност

- **Независим, неофициален проект.** DSTool **не е свързан с**, одобрен от или поддържан от
  **Информационно обслужване АД** или **StampIT**. Имената „StampIT" и „DSTool" принадлежат на
  съответните си притежатели и се използват единствено за обозначаване на съвместимост.
- **Създаден до голяма степен с изкуствен интелект.** Значителна част от кода е генерирана с
  помощта на AI асистент. Прегледайте и тествайте внимателно, преди да разчитате на инструмента
  за правно значими подписи.
- **Без гаранция, на ваша отговорност.** Софтуерът се предоставя **„КАКТО Е" (AS IS)**, без
  каквито и да е гаранции. Използването е изцяло по ваша преценка и на ваша отговорност
  (вж. [LICENSE](LICENSE)).
- **Без мрежова дейност.** DSTool **не извършва никакви мрежови заявки** — няма входящ или
  изходящ мрежов трафик. Не се свързва с никакви сървъри (без OCSP, CRL, времеви печат или AIA);
  цялото подписване се случва локално, между инструмента и вашата карта. Това е лесно проверимо
  с мрежов монитор.

---

## Какво е DSTool

**DSTool** е инструмент за команден ред за **macOS** и **Linux**, който подписва документи с
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

Този проект запълва празнината: отворен код, който върши същата работа на macOS — подписване с
вашата карта/токен директно през стандартния **PKCS#11** интерфейс на драйвера, без нужда от
Windows или платени инструменти на трети страни.

## Инсталация

Изтеглете двоичния файл за вашата система от страницата **Releases**:

- **macOS (Apple Silicon)** — `dstool-<версия>-macos-arm64`. На Intel машини работи през
  Rosetta 2 (или ползвайте jpackage пакета).
- **Linux (x86_64)** — `dstool-<версия>-linux-x86_64`.

### macOS

1. Проверете контролната сума:
   ```sh
   shasum -a 256 -c dstool-<версия>-macos-arm64.sha256
   ```
2. Премахнете атрибута за карантина (файлът не е нотаризиран от Apple) и при нужда го
   подпишете локално:
   ```sh
   xattr -dr com.apple.quarantine ./dstool-<версия>-macos-arm64
   # ако macOS все още твърди, че файлът е „повреден" (Apple Silicon):
   codesign -s - ./dstool-<версия>-macos-arm64
   ```
3. Направете го изпълним и (по желание) го сложете в PATH:
   ```sh
   chmod +x ./dstool-<версия>-macos-arm64
   sudo mv ./dstool-<версия>-macos-arm64 /usr/local/bin/dstool
   ```

### Linux

1. Проверете контролната сума, направете файла изпълним и (по желание) го сложете в PATH:
   ```sh
   sha256sum -c dstool-<версия>-linux-x86_64.sha256
   chmod +x ./dstool-<версия>-linux-x86_64
   sudo mv ./dstool-<версия>-linux-x86_64 /usr/local/bin/dstool
   ```
2. Инсталирайте PKCS#11 middleware за картата, напр. OpenSC:
   ```sh
   sudo apt install opensc      # Debian/Ubuntu
   sudo dnf install opensc      # Fedora/RHEL
   ```

> Двоичният файл е **самостоятелен** — не изисква инсталиран Java и не съдържа външни `.jar`
> файлове. (Linux версията е динамично свързана с glibc и е изградена върху по-стара LTS за
> по-широка съвместимост между дистрибуции.)

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

Ако не зададете `--pkcs11-lib`, DSTool търси драйвера на известни пътища според операционната
система (първият наличен се използва). Списъкът е **отправна точка** — потвърдете точния път за
вашата карта.

**macOS**

| Драйвер | Път |
|---------|-----|
| OpenSC | `/Library/OpenSC/lib/opensc-pkcs11.so`, `/opt/homebrew/lib/opensc-pkcs11.so`, `/usr/local/lib/opensc-pkcs11.so` |
| Thales/Gemalto IDPrime, SafeNet | `/usr/local/lib/libIDPrimePKCS11.dylib`, `/usr/local/lib/libeTPkcs11.dylib`, `/Library/Frameworks/eToken.framework/Versions/Current/libeToken.dylib` |
| Charismathics / CardOS | `/usr/local/lib/libcmP11.dylib` |
| Bit4id | `/Applications/PKIManager-bit4id.app/Contents/Resources/etc/libbit4xpki.dylib` |

**Linux**

| Драйвер | Път |
|---------|-----|
| OpenSC | `/usr/lib/x86_64-linux-gnu/opensc-pkcs11.so`, `/usr/lib64/opensc-pkcs11.so`, `/usr/lib/opensc-pkcs11.so` |
| Thales/Gemalto IDPrime, SafeNet | `/usr/lib/x86_64-linux-gnu/libIDPrimePKCS11.so`, `/usr/lib/libIDPrimePKCS11.so`, `/usr/lib/libeTPkcs11.so` |
| Bit4id | `/usr/lib/x86_64-linux-gnu/libbit4xpki.so`, `/usr/lib/libbit4xpki.so` |

## Изграждане от източник

Изисквания: **GraalVM CE 25** (за `native-image`) и системният C toolchain:
**Xcode Command Line Tools** на macOS, или `build-essential` + `zlib1g-dev` (Debian/Ubuntu) /
`gcc glibc-devel zlib-devel` (Fedora/RHEL) на Linux.

```sh
# JDK 25 / GraalVM CE (напр. чрез SDKMAN)
sdk install java 25-graalce
xcode-select --install              # macOS, ако още не е инсталиран
# sudo apt install build-essential zlib1g-dev   # Debian/Ubuntu

# Тестове на JVM (подписване със софтуерен PKCS#12 ключ)
./mvnw test

# Основен резултат: самостоятелен двоичен файл -> target/dstool
./mvnw -Pnative package

# Резервен вариант (само macOS): jpackage пакет с вграден JRE
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
