import React, { useState } from 'react';
import HomeScreen from './screens/HomeScreen';
import PermissionsScreen from './screens/PermissionsScreen';

export default function App() {
  const [setupComplete, setSetupComplete] = useState(false);

  if (setupComplete) {
    return <HomeScreen />;
  }

  return (
    <PermissionsScreen onComplete={() => setSetupComplete(true)} />
  );
}