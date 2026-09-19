# E5573 Control — Android / GitHub Actions

Target device: Huawei E5573Cs-609, tested target firmware family: 21.327.62.00.1431.

This is the initial native Android prototype. It talks directly to 192.168.8.1 over the local Wi-Fi network.

IMPORTANT: Huawei HiLink firmware uses session/token authentication for many control endpoints. This prototype is intentionally the UI/network skeleton; the next iteration should implement the exact SesTokInfo/login flow before distributing a production APK.

## Build in GitHub
Upload this repository to GitHub, then open Actions → Build APK → Run workflow.
The workflow produces an APK artifact.

## Usage
Connect the Android phone to the E5573 Wi-Fi, install the APK, then open it.
