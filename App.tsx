import React from 'react';
import { View, Text, StyleSheet, Button } from 'react-native';
import { NativeModules } from 'react-native';
const { DummyModule, PackageManagerModule } = NativeModules;

console.log('MODULE:', DummyModule);
console.log('INFO:', typeof DummyModule?.shout);

DummyModule?.shout();

const App = () => {
  return (
    <View style={styles.container}>
      <Text style={styles.text}>Hello, World!</Text>
      <Button title="Enable Overlay Permission" onPress={() => PackageManagerModule?.requestOverlayPermission()} />
        <Button title="Enable Accessibility Permission" onPress={() => PackageManagerModule?.requestAccessibilityPermission()} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  text: {
    fontSize: 20,
    fontWeight: 'bold',
  },
});

export default App;