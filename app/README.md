# 聚芯节点 APP

This is a new Expo/React Native client. It does not import the legacy WeChat mini-program runtime or credentials.

## Development

```bash
npm install
EXPO_PUBLIC_API_BASE_URL=http://127.0.0.1:8091 npm start
```

In another terminal, the matching local backend can be started with
`cd ../app-backend && mvn spring-boot:run -Dspring-boot.run.profiles=local`.

Use `npm run web` for a browser preview or `npm run android` / `npm run ios` for a native development build. Native credentials are stored with `expo-secure-store`; the browser preview uses local storage only for development.

## Native builds

Install and authenticate with EAS CLI, then run `eas build --profile preview --platform android` for an installable APK. Use `eas build --profile production --platform all` for store builds after configuring the Android/iOS signing credentials in EAS.

The first screen implements phone + Alibaba Cloud SMS login. After signing in, the app has native-style Home, Devices, Earnings and Profile tabs, node detail/解绑, pull-to-refresh, and profile editing. Device and earning data are read only from the independent APP API; no mini-program token or local user data is imported.

The API server is configured through `EXPO_PUBLIC_API_BASE_URL`; do not put Alibaba Cloud AccessKey values in this project. The client calls `/api/app/devices`, `/api/app/dashboard/summary`, `/api/app/earnings`, and `/api/auth/me` for profile updates.

## Release identity

- iOS bundle ID: `cn.juxin.orin.app`
- Android package: `cn.juxin.orin.app`
- Deep-link scheme: `juxinorin`
