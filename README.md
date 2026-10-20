# ShaderPatcher

Turn a shaderpack the user already has (the *base*) into a patched pack, using a small patch file you ship with your java program (likely a mod).
The library checks that the user's base is exactly the release your patch was made for, applies the patch, and writes the result.

* **One jar, no dependencies.** Everything it needs is bundled and relocated, so it cannot clash with other libraries.
* Pure Java **8+** tested also up to j25 and on windows, linux and mac
* The base is **never modified**, and the patched pack appears under its final name only when it is complete.
* **A lot** of this library is taken from the [**EuphoriaPatcher mod**](https://github.com/EuphoriaPatches/EuphoriaPatcher) originally and repurposed here

## Example

[**PatcherModExample**](https://github.com/EuphoriaPatches/PatcherModExample) is a complete Fabric and NeoForge muliloader example mod that uses this library:
it bundles the library, generates a patch, gets the shaderpacks folder and applies the patch with both output naming options.
It is the quickest way to see how everything fits together.

## Install

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation 'com.euphoriapatches:shaderpatcher:0.1.3'
}
```

```xml
<dependency>
    <groupId>com.euphoriapatches</groupId>
    <artifactId>shaderpatcher</artifactId>
    <version>0.1.3</version>
</dependency>
```

## Applying a patch

Applying a patch takes three steps: create a patcher for your patch file, call `apply`, and handle what can go wrong.

### 1. Create the patcher

```java
ShaderPatcher patcher = ShaderPatcher
        .builder(PatchSource.classpath(MyMod.class, "MyPatch_1.0.patch"))
        .build();
```

`PatchSource.classpath` finds the patch file inside your jar. Pass any class from that jar (for example your main class) so
the lookup uses the right class loader. `build()` reads and checks the patch file's header
only, so it is cheap. A patcher is immutable and thread-safe, so create it once and reuse it.

### 2. Apply it

```java
try {
    PatchResult result = patcher.apply(Paths.get("shaderpacks/BasePack_r5.zip"), Paths.get("shaderpacks"));
    System.out.println("Installed to " + result.getOutputDir());
} catch (BaseSizeMismatchException e) {
    // wrong version or incomplete base pack: e.getPath() is the file, e.getExpected()/getActual() the numbers
} catch (BaseHashMismatchException e) {
    // right size, modified content
} catch (ShaderPatcherException e) {
    // everything else, see the error table below
}
```

`apply(base, targetDir)` checks that the base is the pack the patch was made for, applies the patch and writes the result
to `targetDir/<name>`. The base can be a `.zip` or a folder and is only read, never modified. The patched pack appears under its final name
only when it is complete, so a failure never leaves a half-finished folder behind.

By default `<name>` is `<base file name without .zip> + <patchName><patchVersion>`, for example `BasePack_r5 + MyPatch_1.0` or `ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.10.5`

### Builder options

All optional, set them before `build()`:

| Option                                                       | What it does                                                                                                                              | Default                        |
|--------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------|
| `.expectPatchName("MyPatch")`, `.expectPatchVersion("_1.0")` | `build()` fails with `PatchMetadataMismatchException` if the patch file is not the one you expect, which catches packaging mistakes       | not checked                    |
| `.outputNaming(OutputNaming.PATCH_ONLY)`                     | name the result just `MyPatch_1.0`, the base name plays no role. Or pass your own: `.outputNaming(context -> "...")`                      | `OutputNaming.BASE_PLUS_PATCH` |
| `.existingOutput(ExistingOutput.REPLACE)`                    | replace an existing result folder once the new one is complete, instead of failing with `OutputExistsException`                           | `FAIL`                         |
| `.debugLog(message -> myLogger.debug(message))`              | receives diagnostic lines such as how many files were extracted or which entries were skipped. The library never logs or prints by itself | no output                      |

### Other methods

| Method                                          | What it does                                                                                             |
|-------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| `verifyBase(path)`                              | checks a base pack without applying anything, throws the same exceptions as `apply`                      |
| `matchesBase(path)`                             | the same check, but returns `true`/`false` and never throws                                              |
| `matchesBase(path, false)`                      | only compares the archive size, which is much faster. Handy to find candidates in a folder full of packs |
| `getMetadata()`                                 | names, versions and fingerprints stored in the patch file                                                |
| `getOutputName(base)`                           | the folder name `apply` would create for this base, so you can check if the result already exists        |
| `ShaderPatcher.fingerprint(path)`               | size and SHA-256 of a pack's archive, for tools                                                          |
| `ShaderPatcher.extract(zipOrFolder, targetDir)` | copies a folder or unpacks a zip into a target folder                                                    |

## Generating a patch

```java
PatchSpec spec = PatchSpec.builder()
        .patchName("MyPatch").patchVersion("_1.0")
        .baseName("BasePack").baseVersion("_r5")       // optional, used for messages and name matching
        .build();

GeneratedPatch patch = PatchGenerator.generate(GenerateRequest.builder(spec)
        .base(Paths.get("BasePack_r5.zip"))             // exactly what your users have
        .edited(Paths.get("MyPatchedPack"))             // the finished edit of BasePack, zip or folder which only you have on your own device
        .editedExclude(name -> name.endsWith(".py"))    // Files can be excluded here which you don't want users to receive
        .addOutputDir(Paths.get("src/main/resources"))  // call again to write copies elsewhere
        .build());
// writes MyPatch_1.0.patch, with the base fingerprint stored inside: nothing to copy anywhere
```

The new patch is always applied to the base in memory and the result compared with your edited pack byte for byte before
anything is written.

## Errors

The library never logs or prints. Everything that can go wrong is a subclass of the checked
`ShaderPatcherException`; the class name tells you who has to act.

| Exception                        | Thrown when                                                                                              | Who acts                                                                          |
|----------------------------------|----------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|
| `BaseSizeMismatchException`      | the base archive has another size: other version, newer/dev release, incomplete download                 | user                                                                              |
| `BaseHashMismatchException`      | same size, different content: edited or unofficial pack                                                  | user                                                                              |
| `InvalidPackException`           | base missing, not a real zip, damaged zip, or an entry that would escape the target folder               | user                                                                              |
| `PatchNotFoundException`         | the patch file cannot be opened, usually a build/packaging mistake                                       | developer                                                                         |
| `PatchCorruptException`          | patch damaged, truncated, wrong magic, unsupported version, text-mode transfer damage, wrong result hash | developer                                                                         |
| `PatchMetadataMismatchException` | `expectPatchName/Version` does not match the patch file                                                  | developer                                                                         |
| `OutputExistsException`          | output folder exists and the policy is `FAIL`                                                            | calling program, handle that however you want, I do a catch() and log to the user |
| `PackIoException`                | disk problem: permissions, full disk, locked file                                                        | environment                                                                       |
| `PatchGenerationException`       | generator only: diff failed or the self-check found a difference                                         | developer                                                                         |

All exceptions carry `getPath()` and the original cause.
Wrong use of the API (null arguments, empty names) throws
`IllegalArgumentException` / `IllegalStateException`.

## Compatibility and stability

A patch records the archive rules it was generated with (`tarFormat`) and the version of its container layout; a library refuses a patch with a version it does not
know, with a clear `PatchCorruptException`, instead of failing mysteriously. Will likely not change tho because I have used this for so long in EP already XD

## Third-party software

The jar bundles Apache Commons Compress, IO, Lang and Codec (Apache License 2.0) and jbsdiff (BSD 2-Clause), relocated into
`com.euphoriapatches.shaderpatcher.internal.shaded`. Their licenses and notices are in `META-INF/licenses/` inside the jar
and in [third-party/](third-party/).

## Building

```
./gradlew check            # tests on Java 8, 11, 17, 21 , 25 and against the shaded jar alone
./gradlew publishToMavenLocal
```

## License

[Mozilla Public License 2.0](LICENSE).
