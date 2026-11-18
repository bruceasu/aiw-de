---
name: java-s3-sdk
description: Implement or review Java S3 SDK code using the project dependency version and local verification.
---

# Java S3 SDK

Use for Java tasks that implement or review object storage with S3.

1. Read the project dependency declaration and existing client configuration. Use the installed SDK version and the supplied API references to confirm method signatures.
2. Keep bucket, object key and region configurable where the project requires them. Follow the project's client ownership, retry and error handling conventions.
3. Verify request construction and error handling locally using the project's mocks or an existing local emulator. Use test data and test credentials only. No live S3 request is needed.
4. Report the changed files, sources used, commands and results. If the dependency or references are missing, report the gap before claiming that the code works.
