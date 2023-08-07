# Release

The process for releasing a new version of the package:

1. Update `version` in the `build.sbt`
2. Update `CHANGELOG.md` with the new version and the changes following [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
3. Create a new git tag and a Github release
4. Publish the artifact using the following process:
    1. Set the key environment variable
        ```bash
        PGP_SIGNING_KEY_ID=<YOUR_KEY>
        ```
    2. Run sbt by setting the key.
        ```bash
        sbt \
            "set pgpSigningKey := Some(\"$PGP_SIGNING_KEY_ID\")"
        sbt clean
        sbt publishSigned
        sbt sonatypeBundleRelease
        ```