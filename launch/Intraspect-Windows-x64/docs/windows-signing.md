# Windows SmartScreen and code signing

[← README](../README.md)

## Current release

**Intraspect v1.0 is unsigned.** Windows SmartScreen may display “Windows protected your PC” and **Unknown publisher**. This warning does not mean a Java library is missing.

If you downloaded this release from [Docker2201/Intraspect Releases](https://github.com/Docker2201/Intraspect/releases) and trust it, choose **More info → Run anyway**. If “Run anyway” is already visible, use that button. On a managed computer that blocks this action, contact its administrator. Do not disable Defender or SmartScreen.

Each release includes `SHA256SUMS.txt`. To check the downloaded ZIP:

```powershell
Get-FileHash .\Intraspect-Windows-x64.zip -Algorithm SHA256
```

Compare the result with that release's checksum file. A matching checksum checks file integrity; it is **not** a code-signing certificate or a guarantee of safety.

You can also download the [source](https://github.com/Docker2201/Intraspect) and [build it yourself](development.md).
