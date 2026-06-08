# Third-party licenses

DSTool's own source code is licensed under the **Apache License, Version 2.0**.

The distributed binary (the GraalVM native image, or the jpackage bundle) statically
incorporates the third-party components below, each under its own license. Versions are
those resolved by the build at the time of writing; check `./mvnw dependency:tree` for the
exact set in a given build.

| Component | Coordinates | Version | License |
|-----------|-------------|---------|---------|
| EU DSS (Digital Signature Service) | `eu.europa.ec.joinup.sd-dss:dss-cades`, `dss-cms-object`, `dss-token`, `dss-utils-apache-commons` (+ transitive `dss-spi`, `dss-model`, `dss-document`, `dss-enumerations`, `dss-alert`, `dss-cms`, `dss-crl-parser`, `dss-utils`) | 6.4 | **LGPL-2.1** |
| Bouncy Castle | `org.bouncycastle:bcprov-jdk18on`, `bcpkix-jdk18on`, `bcutil-jdk18on` | 1.83 | Bouncy Castle license (MIT-style) |
| picocli | `info.picocli:picocli` | 4.7.7 | Apache-2.0 |
| SLF4J | `org.slf4j:slf4j-api`, `slf4j-simple` | 2.0.x | MIT |
| Apache Commons | `commons-io`, `commons-codec`, `commons-lang3`, `commons-collections4` | (transitive) | Apache-2.0 |

## EU DSS — LGPL-2.1

EU DSS is licensed under the **GNU Lesser General Public License, version 2.1**. DSTool
uses DSS as a library without modifying it. Because DSTool's primary distribution is a
single statically-linked native binary, the LGPL §6 "relink" obligation is met by
publishing the complete, buildable source — see **[COMPLIANCE.md](COMPLIANCE.md)**.

Upstream: <https://github.com/esig/dss> — DSS source for the exact version is available
from Maven Central and the European Commission's Digital Building Blocks repository.

## Bundled Java runtime

The jpackage fallback bundle additionally ships a trimmed **Eclipse Temurin / GraalVM**
runtime under the **GPLv2 + Classpath Exception**, whose Classpath Exception explicitly
permits bundling and redistribution without imposing the GPL on DSTool. The native binary
embeds GraalVM SubstrateVM runtime code under the same terms.
