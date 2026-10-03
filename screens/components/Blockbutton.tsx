import React, { useEffect, useState } from 'react';
import { StyleSheet, Text, TouchableOpacity } from 'react-native';
import { Colors } from '../scripts/colors';
import { isAppBlocked } from '../scripts/blockState';
import type { UnblockingMode } from '../BlockingScreen';

type Props = {
  label: string;
  pkg: string;
  /** Called on tap: 'block' if the app isn't blocked yet, 'unblock' if it is. */
  onPress: (mode: UnblockingMode) => void;
};

/**
 * Shows whether one app is currently blocked (every counted reel then triggers an immediate
 * back gesture, in TrackerService.onReelCounted). It no longer changes anything itself: a tap
 * opens the Unblocking screen, which asks for a confirmation to block, or a 20-second hold to
 * unblock - see screens/Unblocking.tsx.
 */
const Blockbutton = ({ label, pkg, onPress }: Props) => {
  const [blocked, setBlocked] = useState(false);

  // State comes from the native side, since blocking is meant to survive the app being closed
  // and reopened. Home is re-mounted when the Unblocking screen closes, so this re-reads it.
  useEffect(() => {
    isAppBlocked(pkg).then(setBlocked);
  }, [pkg]);

  return (
    <TouchableOpacity
      activeOpacity={0.85}
      style={[styles.card, blocked && styles.cardBlocked]}
      onPress={() => onPress(blocked ? 'unblock' : 'block')}
    >
      <Text numberOfLines={1} style={styles.label}>
        {blocked ? `Blocking ${label}` : `Block ${label}`}
      </Text>
      {blocked && <Text style={styles.hint}>Tap to unblock</Text>}
    </TouchableOpacity>
  );
};

export default Blockbutton;

const styles = StyleSheet.create({
  card: {
    flex: 1,
    backgroundColor: Colors.orange,
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
    borderBottomLeftRadius: 4,
    borderBottomRightRadius: 4,
    paddingVertical: 14,
    height: 80,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  cardBlocked: {
    backgroundColor: Colors.green,
  },
  label: {
    color: Colors.text,
    fontFamily: 'WorkSans-Bold',
    fontSize: 14,
    textAlign: 'center',
  },
  hint: {
    color: Colors.text,
    fontFamily: 'WorkSans-Medium',
    fontSize: 10,
    opacity: 0.8,
    marginTop: 2,
  },
});
