![MBARI logo](src/site/resources/images/logo-mbari-3b.png)

# jsharktopoda

[![Build Status](https://travis-ci.org/mbari-org/jsharktopoda.svg?branch=master)](https://travis-ci.org/mbari-org/jsharktopoda)

![Sharktopoda](src/site/resources/images/jsharktopoda2.jpg)

JavaFX-based video player for macOS, Linux, and Windows. Provides remote UDP interface for integration with other apps such as [VARS Annotation](https://github.com/mbari-org/vars-annotation). See [REQUIREMENTS.md](https://github.com/mbari-org/Sharktopoda/blob/main/Requirements/UDP_Remote_Protocol.md) for the UDP remote interface specification.

A java implementation of a remote control is available in the [vcr4j-remote](https://github.com/mbari-org/vcr4j/tree/master/vcr4j-remote) module of [vcr4j](https://github.com/mbari-org/vcr4j)

![Screenshot](src/site/resources/images/jsharktopoda.png)

## Build

Builds are tested using Java 21.

```bash
gradlew clean jpackage --info
```

The generated applications are:

- Applicaiton image including packaged JDK: `build/image`
- Application packaged for OS: `build/jpackage`

### Mac Code Signing

```bash
export MAC_CODE_SIGNER=<your code signer id>

cd jsharktopoda

./gradlew clean jpackage --info

cd  build/jpackage

ditto -c -k --keepParent "jsharktopoda.app" "jsharktopoda.zip"

xcrun notarytool submit "jsharktopoda.zip" \
    --wait \
    --team-id <your team id> \
    --apple-id <your apple dev account email> \
    --password <your app signing password>

xcrun stapler staple "jsharktopoda.app"

rm "jsharktopoda.zip"

ditto -c -k --keepParent "jsharktopoda.app" "jsharktopoda-$(git describe --tags)-mac-$(arch).zip"
```
