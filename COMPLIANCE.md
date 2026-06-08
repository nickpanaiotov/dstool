# LGPL-2.1 compliance for the DSTool binary

DSTool links **EU DSS**, which is licensed under the **GNU Lesser General Public License,
version 2.1 (LGPL-2.1)**. DSTool's own code is Apache-2.0. This document explains how the
distributed binaries satisfy the LGPL.

## The situation

DSTool's primary artifact is a **single statically-linked native binary** (GraalVM
native-image). DSS bytecode is ahead-of-time compiled **into** that one executable, so the
usual LGPL compliance route for Java apps — "ship DSS as a separate, replaceable `.jar`" —
does not apply. The jpackage fallback bundle *does* keep DSS as separate JARs, but the
native binary does not.

## How LGPL-2.1 §6 is satisfied

LGPL-2.1 §6 requires that, when you convey an executable that incorporates the Library, the
recipient must be able to **modify the Library and relink** to produce a modified
executable. §6 lists "provide the object files" as one option; **providing complete source
plus a build recipe is a superset of that** — it lets the user rebuild from scratch with a
modified DSS.

DSTool meets this by **publishing the complete, buildable source**:

1. **Corresponding source of DSS.** DSTool does not modify DSS. The complete corresponding
   source of the exact version (**DSS 6.4**) is publicly available from Maven Central and
   from <https://github.com/esig/dss> (tag `6.4`). This file records the exact version so it
   can always be retrieved.

2. **Source + build recipe for DSTool.** This repository, at the release tag, contains the
   full source, the pinned Maven Wrapper, and the `native` build profile. Anyone can:
   - check out the release tag,
   - replace DSS with a modified build (publish a patched `dss-*` artifact, e.g. via
     `mvn install`, or point the `pom.xml` at a forked artifact),
   - run `./mvnw -Pnative package`,
   - and obtain a native binary containing the modified DSS.

   That is exactly the "modify the Library and relink" capability LGPL-2.1 §6 guarantees.

3. **No separate object-file deliverable is required**, because the complete corresponding
   source and the toolchain pin (GraalVM CE 25) are public and sufficient for a clean
   rebuild.

## Caveats stated honestly

- The relink mechanism is **"rebuild from published source with a modified DSS,"** not a
  jar swap — because native-image AOT-compiles DSS into the binary.
- Bouncy Castle (pulled transitively by DSS) is under the permissive Bouncy Castle/MIT-style
  license and imposes no copyleft obligation; it is listed for attribution in
  [THIRD-PARTY-LICENSES.md](THIRD-PARTY-LICENSES.md).
- Apache-2.0 (DSTool's own code) and LGPL-2.1 (DSS) are compatible for this combination.
