import { Slot } from 'expo-router';

// Expo Router owns the production entry point; this fallback keeps the template entry type-safe.
export default function App() {
  return <Slot />;
}
