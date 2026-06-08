# Принос към DSTool

Благодарим за интереса! Приносите са добре дошли.

## Изисквания за разработка

- **GraalVM CE 25** (съдържа `native-image`). Например чрез SDKMAN:
  ```sh
  sdk install java 25-graalce
  ```
- **Xcode Command Line Tools** (`xcode-select --install`) — `native-image` ползва системния
  toolchain на macOS.
- Не е нужно да инсталирате Maven — използвайте вградения wrapper `./mvnw`.

## Изграждане и тестване

```sh
./mvnw test                  # юнит тестове (подписване със софтуерен PKCS#12 ключ, без карта)
./mvnw -Pnative package      # самостоятелен native двоичен файл -> target/dstool
./mvnw -Pjpackage package    # резервен пакет с вграден JRE
```

### Тестване с виртуален токен (SoftHSM2)

Може да проверите целия път на подписване без физическа карта:

```sh
brew install softhsm opensc
export SOFTHSM2_CONF=/tmp/softhsm2.conf   # сочещ към директория за токени
softhsm2-util --init-token --slot 0 --label dstool --pin 1234 --so-pin 5678
# импортирайте ключ + сертификат с един и същ CKA_ID, после:
DSTOOL_PIN=1234 ./target/dstool list-keys --pkcs11-lib /opt/homebrew/lib/softhsm/libsofthsm2.so
```

## Указания за код

- **Потребителските съобщения са на български** (помощ, грешки, подкани). Коментарите в кода са
  на английски.
- **Не „shade"-вайте зависимостите.** Bouncy Castle се разпространява като подписан `.jar`;
  обединяването му разваля проверката на JCE доставчика. За резервния пакет зависимостите се
  копират в `lib/` без преопаковане. Това запазва и LGPL границата на DSS.
- При промяна на PKCS#11 конектора или на пътищата за подписване, **регенерирайте
  reachability метаданните** за native image с tracing агента (вижте
  `src/main/resources/META-INF/native-image/org.dstool/dstool/`):
  ```sh
  java -agentlib:native-image-agent=config-merge-dir=src/main/resources/META-INF/native-image/org.dstool/dstool \
       --enable-native-access=ALL-UNNAMED -cp <classpath> org.dstool.DsTool sign --pkcs11-lib <lib> <файл>
  ```
  > **Внимание (FFM downcalls):** всяка нова PKCS#11 функция с **различен брой/тип аргументи**
  > въвежда нова форма на native извикване, за която native image трябва да има предварително
  > генериран stub. Ако формата липсва в секцията `foreign` на `reachability-metadata.json`,
  > двоичният файл се проваля по време на изпълнение с
  > `Cannot perform downcall with leaf type ...`. Добавете съответния
  > `{ "returnType": ..., "parameterTypes": [...] }` (`jlong`/`void*`/`jbyte`) и пресъберете.
  > Агентът я улавя само ако реалното извикване е било изпълнено (т.е. с истинска карта/SoftHSM).
- EU DSS идва от Maven Central; то е под **LGPL-2.1** — не го модифицирайте в дървото на проекта
  (вижте [COMPLIANCE.md](COMPLIANCE.md)).

## Лиценз на приноса

С изпращането на принос се съгласявате той да бъде лицензиран под **Apache License 2.0**.
