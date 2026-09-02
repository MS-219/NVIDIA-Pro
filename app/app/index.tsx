import { Redirect } from 'expo-router';
import { ActivityIndicator, StyleSheet, View } from 'react-native';
import { useAuth } from '../context/AuthContext';

export default function Index() {
  const { session, loading } = useAuth();
  if (loading) {
    return (
      <View style={styles.loading}>
        <ActivityIndicator color="#4f7d22" />
      </View>
    );
  }
  return <Redirect href={session ? '/(tabs)/home' : '/login'} />;
}

const styles = StyleSheet.create({
  loading: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: '#f3f6f0' },
});
