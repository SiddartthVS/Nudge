import React, { useEffect, useRef, useState } from 'react';
import { Animated, StyleSheet, Text, TouchableOpacity } from 'react-native';
import { Colors } from '../scripts/colors';
import { isAppBlocked, setAppBlocked } from '../scripts/blockState';

/** How long a press-and-hold must last to turn blocking back off. */
const HOLD_TO_DISABLE_MS = 20000;

type Props = {
  label: string;
  pkg: string;
};

/**
 * A toggle for one app: tap once to start blocking it (every counted reel then triggers an
 * immediate back gesture, in TrackerService.onReelCounted), hold for 20 seconds to stop. The
 * long hold is deliberate friction, so turning blocking off takes a real decision, not a
 * stray tap. The fill that creeps across the button while held is the only feedback that the
 * hold is registering and how close it is to the 20 seconds.
 */
const Blockbutton = ({ label, pkg }: Props) => {
  const [blocked, setBlocked] = useState(false);
  const progress = useRef(new Animated.Value(0)).current;

  // Starting state comes from the native side, since blocking is meant to survive the app
  // being closed and reopened.
  useEffect(() => {
    isAppBlocked(pkg).then(setBlocked);
  }, [pkg]);

  const enable = () => {
    if (blocked) {
      return;
    }
    setAppBlocked(pkg, true);
    setBlocked(true);
  };

  const disable = () => {
    if (!blocked) {
      return;
    }
    setAppBlocked(pkg, false);
    setBlocked(false);
    progress.setValue(0);
  };

  const startHold = () => {
    if (!blocked) {
      return;
    }
    progress.setValue(0);
    Animated.timing(progress, {
      toValue: 1,
      duration: HOLD_TO_DISABLE_MS,
      useNativeDriver: false,
    }).start();
  };

  const cancelHold = () => {
    Animated.timing(progress, { toValue: 0, duration: 150, useNativeDriver: false }).start();
  };

  return (
    <TouchableOpacity
      activeOpacity={0.85}
      style={[styles.card, blocked && styles.cardBlocked]}
      onPress={enable}
      onLongPress={disable}
      delayLongPress={HOLD_TO_DISABLE_MS}
      onPressIn={startHold}
      onPressOut={cancelHold}
    >
      <Animated.View
        pointerEvents="none"
        style={[
          styles.fill,
          { width: progress.interpolate({ inputRange: [0, 1], outputRange: ['0%', '100%'] }) },
        ]}
      />
      <Text numberOfLines={1} style={styles.label}>
        {blocked ? `Blocking ${label}` : `Block ${label}`}
      </Text>
      {blocked && <Text style={styles.hint}>Hold 20s to stop</Text>}
    </TouchableOpacity>
  );
};

export default Blockbutton;

const styles = StyleSheet.create({
  card: {
    flex: 1,
    backgroundColor: Colors.orange,
    borderRadius: 16,
    paddingVertical: 14,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  cardBlocked: {
    backgroundColor: Colors.green,
  },
  fill: {
    position: 'absolute',
    left: 0,
    top: 0,
    bottom: 0,
    backgroundColor: 'rgba(0,0,0,0.25)',
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
