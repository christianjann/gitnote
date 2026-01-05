set windows-powershell := true

# You need to open the project once in Android studio, then it should be there

export JAVA_HOME := `grep '^java.home=' .gradle/config.properties | cut -d'=' -f2`

main:
    just --list

get-wrapper:
    #curl -L -o gradle/wrapper/gradle-wrapper.jar https://github.com/gradle/gradle/raw/v8.13.0/gradle/wrapper/gradle-wrapper.jar
    git lfs pull

fix:
    ./gradlew lintFix

fmt-just:
    just --fmt --unstable

fmt-prettier:
    # install on Debian: sudo snap install node --classic
    # npx is the command to run npm package, node is the runtime
    npx prettier -w . --ignore-path ./.gitignore --ignore-path ./.prettierignore

fmt-kotlin:
    # Format Kotlin files using Android lint fix
    ./gradlew lintFix

fmt-rust:
    # Format all Rust files using cargo fmt
    cd app/src/main/rust && cargo fmt

# Format all code files
fmt: fmt-kotlin fmt-rust fmt-just fmt-prettier

test:
    # Run all Rust tests (including integration tests)
    cd app/src/main/rust && cargo test

test-all:
    # Run all tests: Rust and Android
    echo "Running Rust tests..."
    cd app/src/main/rust && cargo test
    echo "Running Android unit tests..."
    ./gradlew testDebugUnitTest

sort-supported-extension:
    #!/usr/bin/env bash
    extension_dir=app/src/main/rust/supported_extensions
    for f in $(ls $extension_dir 2>/dev/null); do
    	sort $extension_dir/$f -o $extension_dir/$f
        echo sorted $f
    done

rust-build:
    #!/usr/bin/env bash
    cd app/src/main/rust
    make DEBUG=0 build_install

build:
    ./gradlew :app:assembleDebug

install:
    ./gradlew :app:assembleDebug :app:installDebug

clean:
    ./gradlew clean

clean-build:
    ./gradlew clean :app:assembleDebug

nightly-build:
    ./gradlew :app:assembleNightly

nightly-install:
    ./gradlew :app:assembleNightly :app:installNightly

generate-release-keys:
    ./generate-release-keys.sh

setup-release-env:
    ./setup-release-env.sh

release-build:
    #!/usr/bin/env bash
    echo "Running all tests before release build..."
    just test-all
    echo "✅ All tests passed!"
    echo "Setting up release environment..."
    source ./setup-release-env.sh
    echo "Building release APK..."
    ./gradlew :app:assembleRelease

release-install:
    #!/usr/bin/env bash
    echo "Setting up release environment..."
    source ./setup-release-env.sh
    echo "Building and installing release APK..."
    ./gradlew assembleRelease
    echo "Checking for connected device..."
    if ! adb devices | grep -q "device$"; then
        echo "❌ No device connected. Connect a device or start an emulator first."
        echo "📱 To install manually: adb install app/build/outputs/apk/release/app-release.apk"
        exit 1
    fi
    echo "Installing release APK to device..."
    adb install -r app/build/outputs/apk/release/app-release.apk
    echo ""
    echo "✅ Release APK built and installed successfully!"

release-package:
    #!/usr/bin/env bash
    echo "Running all tests before creating release package..."
    just test-all
    echo "✅ All tests passed!"

    echo "Checking if git is clean..."
    if ! git diff --quiet || ! git diff --staged --quiet; then
        echo "❌ Git is not clean. Please commit or stash changes."
        exit 1
    fi
    echo "✅ Git is clean."

    # Get versionName from build.gradle.kts
    VERSION_NAME=$(grep 'versionName =' app/build.gradle.kts | sed 's/.*versionName = "\(.*\)".*/\1/')
    echo "Version name: $VERSION_NAME"

    # Create git tag
    echo "Creating git tag v$VERSION_NAME..."
    git tag "v$VERSION_NAME"
    git push origin "v$VERSION_NAME"

    # Do release-build
    echo "Building release APK..."
    just release-build

    # Create packages folder if not exists
    mkdir -p packages

    # Move APK
    echo "Moving APK to packages/gitnote-release-${VERSION_NAME}.apk..."
    mv app/build/outputs/apk/release/app-release.apk packages/gitnotecje-release-${VERSION_NAME}.apk

    # Bump version code and name
    echo "Bumping version..."
    CURRENT_CODE=$(grep 'versionCode =' app/build.gradle.kts | sed 's/.*versionCode = \([0-9]*\).*/\1/')
    NEW_CODE=$((CURRENT_CODE + 1))

    # After release, increment patch or add .0 if no patch
    DOT_COUNT=$(echo "$VERSION_NAME" | tr -cd '.' | wc -c)
    if [ "$DOT_COUNT" -eq 1 ]; then
        # yy.MM -> yy.MM.0
        NEW_NAME="${VERSION_NAME}.0"
    elif [ "$DOT_COUNT" -eq 2 ]; then
        # yy.MM.patch -> yy.MM.(patch+1)
        IFS='.' read -r YEAR MONTH PATCH <<< "$VERSION_NAME"
        NEW_PATCH=$((PATCH + 1))
        NEW_NAME="${YEAR}.${MONTH}.${NEW_PATCH}"
    else
        echo "Unexpected versionName format: $VERSION_NAME"
        exit 1
    fi

    # Update build.gradle.kts
    sed -i "s/versionCode = $CURRENT_CODE/versionCode = $NEW_CODE/" app/build.gradle.kts
    sed -i "s/versionName = \"$VERSION_NAME\"/versionName = \"$NEW_NAME\"/" app/build.gradle.kts

    echo "✅ Release package created: packages/gitnote-release-${VERSION_NAME}.apk"
    echo "Version bumped to $NEW_NAME (code: $NEW_CODE)"

release-package-install:
    #!/usr/bin/env bash
    echo "Finding latest release package..."
    LATEST_PACKAGE=$(ls -t packages/gitnotecje-release-*.apk | head -1)
    if [ -z "$LATEST_PACKAGE" ]; then
        echo "❌ No release packages found in packages/"
        exit 1
    fi
    echo "Latest package: $LATEST_PACKAGE"
    echo "Checking for connected device..."
    if ! adb devices | grep -q "device$"; then
        echo "❌ No device connected. Connect a device or start an emulator first."
        exit 1
    fi
    echo "Installing $LATEST_PACKAGE..."
    adb install -r -d "$LATEST_PACKAGE"
    echo "✅ Release package installed successfully!"
