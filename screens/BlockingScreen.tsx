import React, { useCallback, useEffect, useRef, useState } from 'react';
import { AppState, BackHandler, Pressable, StyleSheet, Text, TouchableOpacity, View, Animated, Easing } from 'react-native';
import { Colors } from './scripts/colors';
import { setAppBlocked } from './scripts/blockState';

export type UnblockingMode = 'block' | 'unblock';

type Props = {
  mode: UnblockingMode;
  label: string;
  pkg: string;
  onClose: () => void;
};

/** 
 * Timers and Thresholds 
 */
const HOLD_TO_UNBLOCK_MS = 20000;
const PHRASE_EVERY_MS = 2500;

/** 
 * Pattern-interrupt phrases displayed during the 20-second hold. 
 */
const PHRASES = [
  'Your goals are waiting...',
  'You blocked this for a reason - Honor it...',
  'We both know this turns into hours...',
  'Trade your time for cheap dopamine?',
  'Remember why you started!',
  'You are in control - Act like it!',
  'Say goodbye to the rest of your day!',
  'Don\'t betray the promise you made to yourself...',
  'Drop the phone. Walk away before you sink!',
  'Your real life is slipping away right now...',
  'Reclaim your mind - Take your finger off the screen!',
  'This urge is a trap. Do not step into it!',
];

function randomPhrase(previous: string | null): string {
  const pool = PHRASES.filter(phrase => phrase !== previous);
  return pool[Math.floor(Math.random() * pool.length)];
}

/**
 * Blocking/Unblocking Confirmation Screen.
 * 
 * Flow:
 * - Block: Instant single-tap confirmation.
 * - Unblock: Requires a continuous 20-second hold. 
 * 
 * Animations:
 * - On Mount: Diagonal bands slide in natively.
 * - On Hold: Progress bar fills (JS thread), bands recede to reveal text (Native thread).
 * - On Interrupt (let go, backgrounded, back button): Timers clear, animations snap back.
 */
const Unblocking = ({ mode, label, pkg, onClose }: Props) => {
  
  // --- State & Refs ---
  const [phrase, setPhrase] = useState<string | null>(null);
  const [secondsLeft, setSecondsLeft] = useState(20);

  const phraseTimer = useRef<ReturnType<typeof setInterval> | null>(null);
  const secondTimer = useRef<ReturnType<typeof setInterval> | null>(null);
  const lastPhrase = useRef<string | null>(null);
  const finished = useRef(false);

  // --- Animations ---
  const holdProgress = useRef(new Animated.Value(0)).current;
  const bandOffset = useRef(new Animated.Value(600)).current;

  // --- Core Logic ---
  const clearTimers = useCallback(() => {
    if (phraseTimer.current) clearInterval(phraseTimer.current);
    if (secondTimer.current) clearInterval(secondTimer.current);
    phraseTimer.current = null;
    secondTimer.current = null;
  }, []);

  const showNextPhrase = () => {
    const next = randomPhrase(lastPhrase.current);
    lastPhrase.current = next;
    setPhrase(next);
  };

  const cancelHold = useCallback(() => {
    clearTimers();
    setPhrase(null);
    setSecondsLeft(20);
    
    holdProgress.stopAnimation();
    Animated.timing(holdProgress, {
      toValue: 0,
      duration: 400,
      easing: Easing.out(Easing.ease),
      useNativeDriver: false, 
    }).start();

    bandOffset.stopAnimation();
    Animated.timing(bandOffset, {
      toValue: 0,
      duration: 400,
      easing: Easing.out(Easing.ease),
      useNativeDriver: true, 
    }).start();
  }, [clearTimers, holdProgress, bandOffset]);

  const finishUnblock = () => {
    if (finished.current) return;
    
    finished.current = true;
    clearTimers();
    setAppBlocked(pkg, false);
    onClose();
  };

  const startHold = () => {
    if (finished.current) return;
    
    showNextPhrase();
    setSecondsLeft(20);

    phraseTimer.current = setInterval(showNextPhrase, PHRASE_EVERY_MS);
    
    secondTimer.current = setInterval(() => {
      setSecondsLeft(prev => Math.max(1, prev - 1));
    }, 1000);

    holdProgress.setValue(0);
    Animated.timing(holdProgress, {
      toValue: 1,
      duration: HOLD_TO_UNBLOCK_MS,
      easing: Easing.linear,
      useNativeDriver: false, 
    }).start(({ finished: animFinished }) => {
      if (animFinished) finishUnblock();
    });

    bandOffset.setValue(0);
    Animated.timing(bandOffset, {
      toValue: 600,
      duration: HOLD_TO_UNBLOCK_MS,
      easing: Easing.linear,
      useNativeDriver: true,
    }).start();
  };

  const confirmBlock = () => {
    if (finished.current) return;
    
    finished.current = true;
    setAppBlocked(pkg, true);
    onClose();
  };

  // --- Lifecycle ---
  useEffect(() => {
    Animated.timing(bandOffset, {
      toValue: 0,
      duration: 500,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: true,
    }).start();
  }, [bandOffset]);

  useEffect(() => {
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      onClose();
      return true;
    });
    return () => subscription.remove();
  }, [onClose]);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', state => {
      if (state !== 'active') cancelHold();
    });
    return () => subscription.remove();
  }, [cancelHold]);

  useEffect(() => clearTimers, [clearTimers]);

  // --- Render Prep ---
  const holding = phrase !== null;

  const fillWidth = holdProgress.interpolate({
    inputRange: [0, 1],
    outputRange: ['0%', '100%']
  });

  const topBandTranslateY = bandOffset.interpolate({
    inputRange: [0, 600],
    outputRange: [0, -600] 
  });

  return (
    <View style={styles.container}>
      
      {/* Background Bands */}
      <Animated.View style={[
        styles.topBand, 
        { transform: [{ translateY: topBandTranslateY }, { rotate: '-12deg' }] }
      ]} />
      <Animated.View style={[
        styles.bottomBand, 
        { transform: [{ translateY: bandOffset }, { rotate: '-12deg' }] }
      ]} />

      {/* Foreground Content */}
      <View style={styles.main}>
        
        <View style={styles.center}>
          <Text maxFontSizeMultiplier={1.2} style={styles.title}>
            {mode === 'block' ? `Block ${label}?` : `Unblock ${label}?`}
          </Text>
          <Text maxFontSizeMultiplier={1.2} style={styles.body}>
            {mode === 'block'
              ? `Nudge will instantly pull you out of every reel you try to watch in ${label}. The choice to unblock is always yours.`
              : `Are you absolutely sure? Hold the button for 20 seconds to break the block. Let go at any moment to keep your streak.`}
          </Text>
        </View>

        <View style={styles.actions}>
          {mode === 'block' ? (
            <>
              <TouchableOpacity activeOpacity={0.85} style={[styles.button, styles.confirm]} onPress={confirmBlock}>
                <Text style={styles.buttonText}>Yes, block</Text>
              </TouchableOpacity>
              
              <TouchableOpacity activeOpacity={0.7} style={styles.cancel} onPress={onClose}>
                <Text style={styles.cancelText}>Cancel</Text>
              </TouchableOpacity>
            </>
          ) : (
            <>
              <View style={styles.phraseBox}>
                {holding && (
                  <Text maxFontSizeMultiplier={1.2} style={styles.phrase}>
                    {phrase}
                  </Text>
                )}
              </View>

              <Pressable 
                style={[styles.button, styles.holdButton]} 
                onPressIn={startHold} 
                onPressOut={cancelHold}
                hitSlop={{ top: 20, bottom: 20, left: 20, right: 20 }}
              >
                <Animated.View pointerEvents="none" style={[styles.fill, { width: fillWidth }]} />
                <Text style={styles.buttonText}>
                  {holding ? `Keep holding... ${secondsLeft}s` : 'Hold to unblock'}
                </Text>
              </Pressable>

              <TouchableOpacity activeOpacity={0.7} style={styles.cancel} onPress={onClose}>
                <Text style={styles.cancelText}>Stay blocked</Text>
              </TouchableOpacity>
            </>
          )}
        </View>
        
      </View>
    </View>
  );
};

export default Unblocking;

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.background,
    overflow: 'hidden',
  },
  topBand: {
    position: 'absolute',
    top: '-15%',
    left: '-20%',
    width: '140%',
    height: '50%',
    backgroundColor: Colors.green,
  },
  bottomBand: {
    position: 'absolute',
    bottom: '-15%',
    left: '-20%',
    width: '140%',
    height: '50%',
    backgroundColor: Colors.orange,
  },
  main: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: '8%',
    zIndex: 1, 
  },
  center: {
    alignItems: 'center',
  },
  title: {
    color: Colors.text,
    fontFamily: 'WorkSans-Black',
    fontSize: 36,
    textAlign: 'center',
    marginBottom: 16,
  },
  body: {
    color: Colors.text,
    fontFamily: 'WorkSans-Medium',
    fontSize: 15,
    lineHeight: 22,
    opacity: 0.7,
    textAlign: 'center',
  },
  actions: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 24,
    alignItems: 'center',
  },
  phraseBox: {
    height: 64,
    justifyContent: 'center',
    paddingHorizontal: '8%',
    marginBottom: 50,
  },
  phrase: {
    color: Colors.orange,
    fontFamily: 'WorkSans-Bold',
    fontSize: 20,
    textAlign: 'center',
  },
  button: {
    width: '75%',
    height: 56,
    borderRadius: 999,
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
  },
  confirm: {
    backgroundColor: Colors.green,
  },
  holdButton: {
    backgroundColor: Colors.grey,
  },
  fill: {
    position: 'absolute',
    left: 0,
    top: 0,
    bottom: 0,
    backgroundColor: Colors.orange,
  },
  buttonText: {
    color: Colors.text,
    fontFamily: 'WorkSans-Black',
    fontSize: 18,
    zIndex: 1,
  },
  cancel: {
    marginTop: 16,
    paddingVertical: 8,
    paddingHorizontal: 20,
  },
  cancelText: {
    color: Colors.text,
    fontFamily: 'WorkSans-SemiBold',
    fontSize: 15,
    opacity: 0.7,
    marginBottom: '8%',
  },
});