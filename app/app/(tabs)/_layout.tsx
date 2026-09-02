import { Ionicons } from '@expo/vector-icons';
import { Redirect, Tabs } from 'expo-router';
import { colors } from '../../components/theme';
import { useAuth } from '../../context/AuthContext';

export default function TabsLayout() {
  const { session, loading } = useAuth();
  if (!loading && !session) return <Redirect href="/login" />;

  return (
    <Tabs screenOptions={{
      headerShown: false,
      tabBarActiveTintColor: colors.leaf,
      tabBarInactiveTintColor: '#9aa59b',
      tabBarStyle: { height: 82, paddingTop: 9, borderTopColor: '#e5ebe2', backgroundColor: '#ffffff' },
      tabBarLabelStyle: { fontSize: 11, fontWeight: '600', paddingBottom: 8 },
    }}>
      <Tabs.Screen name="home" options={{ title: '首页', tabBarIcon: ({ color, size, focused }) => <Ionicons name={focused ? 'home' : 'home-outline'} color={color} size={size} /> }} />
      <Tabs.Screen name="devices" options={{ title: '设备', tabBarIcon: ({ color, size, focused }) => <Ionicons name={focused ? 'hardware-chip' : 'hardware-chip-outline'} color={color} size={size} /> }} />
      <Tabs.Screen name="earnings" options={{ title: '收益', tabBarIcon: ({ color, size, focused }) => <Ionicons name={focused ? 'bar-chart' : 'bar-chart-outline'} color={color} size={size} /> }} />
      <Tabs.Screen name="profile" options={{ title: '我的', tabBarIcon: ({ color, size, focused }) => <Ionicons name={focused ? 'person' : 'person-outline'} color={color} size={size} /> }} />
    </Tabs>
  );
}
