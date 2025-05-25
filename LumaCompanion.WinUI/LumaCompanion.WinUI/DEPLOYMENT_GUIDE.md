# Luma Companion (Windows) - Deployment Guide

This guide provides basic steps and considerations for packaging and deploying the Luma Companion Windows application.

## Prerequisites
*   Windows 10 (Version 2004, Build 19041) or later.
*   .NET Desktop Runtime (currently targeting .NET 8, but verify based on the project's target framework).
*   Windows App SDK Runtime. (MSIX packaging can help manage this dependency).
*   Developer Mode or Sideloading enabled on the target machine if installing MSIX packages outside the Microsoft Store.

## Packaging Options

The recommended approach for distributing a WinUI 3 application is via MSIX packaging.

### 1. Using Visual Studio (Recommended for Full MSIX Features)

If you open this project in Visual Studio 2022 (with the "MSIX Packaging Tools" and ".NET Multi-platform App UI development" workloads installed):

1.  **Add a Packaging Project:**
    *   Right-click the solution in Solution Explorer.
    *   Select "Add" -> "New Project...".
    *   Search for "Windows Application Packaging Project" and add it to your solution (e.g., name it `LumaCompanion.WinUI.Package`).
2.  **Add Application Reference:**
    *   In the Packaging Project, right-click "Dependencies" (or "Applications").
    *   Select "Add Project Reference..." and choose the `LumaCompanion.WinUI` project.
3.  **Configure Package Manifest:**
    *   Open `Package.appxmanifest` in the Packaging Project.
    *   Review settings under "Application" (Display name, Logo, etc.) and "Packaging" (Package name, version, publisher). The publisher information needs to match your code signing certificate subject.
4.  **Build in Release Mode:**
    *   Set your solution configuration to "Release".
5.  **Create App Packages:**
    *   Right-click the Packaging Project.
    *   Select "Publish" -> "Create App Packages...".
    *   Follow the wizard:
        *   **Sideloading:** Choose this for direct installation or enterprise deployment. You'll need to sign the package with a code signing certificate. You can create a temporary self-signed certificate for testing.
        *   **Microsoft Store:** Choose this if you intend to publish to the Store (requires a developer account).
    *   Select architecture (e.g., x64).
    *   The output will be an `.msix` or `.msixupload` file.

### 2. Using .NET CLI (`dotnet publish` for Unpackaged Deployment)

If you prefer an unpackaged deployment (less common for WinUI apps meant for end-users but possible):

1.  **Open a terminal** in the `LumaCompanion.WinUI` project directory (where the `.csproj` file is).
    *   **Note:** The prompt mentions the directory where `.csproj` is, which is `LumaCompanion.WinUI/LumaCompanion.WinUI/`.
2.  **Run the publish command:**
    ```bash
    # Ensure you are in LumaCompanion.WinUI/LumaCompanion.WinUI/
    dotnet publish -c Release --framework <your-target-framework> -r <your-runtime-identifier> --self-contained false
    ```
    *   Replace `<your-target-framework>` (e.g., `net8.0-windows10.0.19041.0`). Check your `.csproj`.
        *   The current `.csproj` shows `net9.0-windows10.0.19041.0`, so this should be updated in the guide.
    *   Replace `<your-runtime-identifier>` (e.g., `win10-x64`).
    *   `--self-contained false` means the .NET runtime needs to be on the target machine. Use `true` to bundle it (larger deployment).
3.  The output will be in `bin/Release/<your-target-framework>/<your-runtime-identifier>/publish/`. This folder contains the executable and all necessary DLLs. `appsettings.json` should be present here.

## API Key Configuration

Remember to configure the necessary API keys (OpenAI, DeepSeek, ElevenLabs) on the deployment machine.
*   For **Packaged Apps (MSIX)**: API keys should ideally not be hardcoded. Consider mechanisms like:
    *   Requiring users to enter them in the app's settings (if the app supports this post-install).
    *   Using a configuration file placed in `ApplicationData.Current.LocalFolder` after first run.
    *   For enterprise scenarios, consider Azure App Configuration or similar.
*   For **Unpackaged Apps**: `appsettings.json` can be deployed alongside the executable. For sensitive keys, consider encrypting sections of `appsettings.json` or using environment variables on the target machine. User Secrets are for development only.

## Important Notes
*   **Code Signing:** MSIX packages *must* be signed. For sideloading, a self-signed certificate can be used for testing if the certificate is trusted on the target machine. For broader distribution, a certificate from a trusted Certificate Authority (CA) is required.
*   **Windows App SDK Runtime:** Ensure the target machine has the Windows App SDK runtime. The MSIX installer can handle this as a dependency if configured correctly, or it might need to be installed separately.
