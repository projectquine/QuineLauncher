# QuineLauncher

## Description
QuineLauncher is a home/launcher app for Android devices that displays a subset of installed apps and hides the rest. The displayed apps are customizable and the app icons can be made bigger. The launcher also shows the device's IP address and the name of the OS.

## Installation
1. Clone the repository.
2. Open the project in Android Studio.
3. Build the project and run on an emulator or physical device.

## Usage
The launcher app will display a subset of installed apps. Tap on an app icon to launch the app. 

## Customization
### Displayed Apps
To customize the displayed apps:
1. Open MainActivity.kt.
2. Edit the `appList` variable to contain the package names of the desired apps.

### Icon Size
To change the size of the app icons:
1. Open app_item.xml.
2. Change the `layout_width` and `layout_height` attributes of the ImageView to the desired size.

### Title and IP Address
To change the title of the launcher and show the device's IP address:
1. Open MainActivity.kt.
2. Edit the `title` variable to the desired title.
3. Edit the `ipAddress` variable to the IP address of the device.
