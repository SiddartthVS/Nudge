import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, Image, AppState, TouchableOpacity } from 'react-native';
import { NativeModules } from 'react-native';
import { Colors } from './colors';
import { useBase, pct } from './scale';
import PermissionCard from './components/PermissionCard';

const { PermissionsModule: pm } = NativeModules;

/**
 * VERTICAL LAYOUT (percent of the screen height, measured from the mockup, adds up to 100):
 *
 *    6.3  top space
 *   27.7  title (4 lines + logo)
 *    3.0  space
 *    0.4  divider
 *    3.0  space
 *    3.9  tracker
 *    4.8  space
 *   33.4  the three permission cards (they space themselves out inside this block)
 *    5.1  space
 *    6.0  "Nudge!" button
 *    6.4  bottom space (whatever is left)
 *
 * Nothing is hard-coded in pixels: sections are % of the height, and things React Native cannot
 * size with a % (fonts, circles, line thickness) use pct(base, x) - see scale.ts.
 */
type Gap = `${number}%`;
const Space = ({ height }: { height: Gap }) => <View style={{ height }} />;

const PermissionsScreen = ({ onComplete }: { onComplete: () => void }) => {
    const [hasOverlay, setHasOverlay] = useState(false);
    const [hasAccess, setHasAccess] = useState(false);
    const [hasBattery, setHasBattery] = useState(false);

    const base = useBase();

    const checkPermissions = async () => {
        try {
            const overlayStatus = await pm.checkOverlayPermission();
            const accessStatus = await pm.checkAccessibilityPermission();
            const batteryStatus = await pm.checkBatteryPermission();

            setHasOverlay(overlayStatus);
            setHasAccess(accessStatus);
            setHasBattery(batteryStatus);
        } catch (error) {
            console.error("Failed to check permissions:", error);
        }
    };

    useEffect(() => {
        checkPermissions();

        const subscription = AppState.addEventListener('change', nextAppState => {
            if (nextAppState === 'active') {
                checkPermissions();
            }
        });

        return () => subscription.remove();
    }, []);

    const isAllGranted = hasOverlay && hasAccess && hasBattery;

    // One entry per tracker round, in the same order as the cards below.
    const granted = [hasOverlay, hasAccess, hasBattery];

    // Tracker sizes (percent of the screen width).
    const ring = pct(base, 9);          // grey outer circle
    const disc = ring * 0.49;           // coloured inner circle (about half the ring)
    const lineBase = pct(base, 1.3);    // grey line behind everything
    const lineActive = pct(base, 0.6);  // white line drawn over it

    return (
        <View style={styles.container}>
            <Space height="6.3%" />

            {/* --- HEADER --- */}
            <View style={styles.titleBox}>
                <Text
                    maxFontSizeMultiplier={1.2}
                    style={[styles.title, { fontSize: pct(base, 13.65), lineHeight: pct(base, 15.9) }]}
                >
                    A few{"\n"}quick{"\n"}steps to{"\n"}Nudge!&nbsp;
                    <Image
                        source={require('../assets/images/logo.png')}
                        style={{ width: pct(base, 13.7), height: pct(base, 13.7) }}
                    />
                </Text>
            </View>

            <Space height="3%" />
            <View style={styles.line} />
            <Space height="3%" />

            {/* --- PROGRESS TRACKER (three rounds joined by a line) --- */}
            <View style={styles.trackerBox}>
                <View style={[styles.track, { height: ring }]}>

                    {/* Layer 1: the grey rings */}
                    <View style={[styles.layer, styles.roundRow]}>
                        {granted.map((_, i) => (
                            <View
                                key={i}
                                style={{
                                    width: ring,
                                    height: ring,
                                    borderRadius: ring / 2,
                                    backgroundColor: Colors.grey,
                                }}
                            />
                        ))}
                    </View>

                    {/* Layer 2: the connecting line, from the centre of the first round to the
                        centre of the last. It sits above the rings and below the coloured discs.
                        A segment turns white only when the two rounds it joins are both done. */}
                    <View style={[styles.layer, { left: ring / 2, right: ring / 2, justifyContent: 'center' }]}>
                        <View style={{ height: lineBase, backgroundColor: Colors.grey }} />
                        <View style={[styles.segmentRow, { height: ring }]}>
                            <View style={{
                                flex: 1,
                                height: lineActive,
                                backgroundColor: hasOverlay && hasAccess ? Colors.text : 'transparent',
                            }} />
                            <View style={{
                                flex: 1,
                                height: lineActive,
                                backgroundColor: hasAccess && hasBattery ? Colors.text : 'transparent',
                            }} />
                        </View>
                    </View>

                    {/* Layer 3: the coloured discs - green when granted, orange when not */}
                    <View style={[styles.layer, styles.roundRow]}>
                        {granted.map((ok, i) => (
                            <View
                                key={i}
                                style={{ width: ring, height: ring, alignItems: 'center', justifyContent: 'center' }}
                            >
                                <View
                                    style={{
                                        width: disc,
                                        height: disc,
                                        borderRadius: disc / 2,
                                        backgroundColor: ok ? Colors.green : Colors.orange,
                                    }}
                                />
                            </View>
                        ))}
                    </View>
                </View>
            </View>

            <Space height="4.8%" />

            {/* --- PERMISSION CARDS --- */}
            <View style={styles.cardsBlock}>
                <PermissionCard
                    title={`Display over apps`}
                    description={`Find Nudge ➜ Allow access`}
                    isGranted={hasOverlay}
                    onPress={() => pm.requestOverlayPermission()}
                />

                <PermissionCard
                    title={`Accessibility service`}
                    description={`Downloaded apps ➜ Nudge ➜ Allow access`}
                    isGranted={hasAccess}
                    onPress={() => pm.requestAccessibilityPermission()}
                />

                <PermissionCard
                    title={`Run in background`}
                    description={`Select No restrictions`}
                    isGranted={hasBattery}
                    onPress={() => pm.requestBatteryPermission()}
                />
            </View>

            <Space height="5.1%" />

            {/* --- NUDGE BUTTON (unlocks once all three are done) --- */}
            <View style={styles.pillBox}>
                <TouchableOpacity
                    activeOpacity={isAllGranted ? 0.7 : 1}
                    onPress={() => {
                        if (isAllGranted) {
                            onComplete();
                        }
                    }}
                    style={[
                        styles.pill,
                        { backgroundColor: isAllGranted ? Colors.orange : Colors.grey }
                    ]}
                >
                    {/* Green rounded fill over the left ~60% of the coral button */}
                    {isAllGranted && <View style={styles.pillFill} />}
                    <Text
                        numberOfLines={1}
                        adjustsFontSizeToFit
                        maxFontSizeMultiplier={1.2}
                        style={[
                            styles.pillText,
                            { fontSize: pct(base, 5.2), color: isAllGranted ? Colors.text : '#858585' }
                        ]}
                    >
                        Nudge!➜
                    </Text>
                </TouchableOpacity>
            </View>
        </View>
    );
};

const styles = StyleSheet.create({
    container: {
        flex: 1,
        backgroundColor: Colors.background,
        paddingHorizontal: '5.5%',
    },
    titleBox: {
        height: '27.7%',
    },
    title: {
        color: Colors.text,
        fontFamily: 'WorkSans-Black',
    },
    line: {
        height: '0.4%',
        backgroundColor: Colors.grey,
    },
    trackerBox: {
        height: '3.9%',
        justifyContent: 'center',
    },
    track: {
        // 91% of the padded width = 81% of the screen, like the mockup.
        width: '91%',
        alignSelf: 'center',
    },
    layer: {
        position: 'absolute',
        top: 0,
        bottom: 0,
        left: 0,
        right: 0,
    },
    roundRow: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
    },
    segmentRow: {
        position: 'absolute',
        top: 0,
        left: 0,
        right: 0,
        flexDirection: 'row',
        alignItems: 'center',
    },
    cardsBlock: {
        height: '33.4%',
        justifyContent: 'space-between',
    },
    pillBox: {
        height: '6%',
        alignItems: 'center',
    },
    pill: {
        // 45% of the padded width = 40% of the screen.
        width: '45%',
        height: '100%',
        borderRadius: 999,
        overflow: 'hidden',
        alignItems: 'center',
        justifyContent: 'center',
    },
    pillFill: {
        position: 'absolute',
        left: 0,
        top: 0,
        bottom: 0,
        width: '62%',
        borderRadius: 999,
        backgroundColor: Colors.green,
    },
    pillText: {
        fontFamily: 'WorkSans-Black',
        textAlign: 'center',
    },
});

export default PermissionsScreen;
