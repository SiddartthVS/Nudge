import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, Image, AppState, TouchableOpacity, NativeModules } from 'react-native';
import { Colors } from './scripts/colors';
import { checkAllPermissions } from './scripts/permissions';
import PermissionCard from './components/PermissionCard';

const { PermissionsModule: pm } = NativeModules;

const PermissionsScreen = ({ onComplete }: { onComplete: () => void }) => {
    const [hasOverlay, setHasOverlay] = useState(false);
    const [hasAccess, setHasAccess] = useState(false);
    const [hasBattery, setHasBattery] = useState(false);

    const checkPermissions = async () => {
        try {
            const status = await checkAllPermissions();
            setHasOverlay(status.hasOverlay);
            setHasAccess(status.hasAccess);
            setHasBattery(status.hasBattery);
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
    const granted = [hasOverlay, hasAccess, hasBattery];

    const RING_SIZE = 36;
    const DISC_SIZE = 18;
    const LINE_BASE = 5;
    const LINE_ACTIVE = 2;

    return (
        <View style={styles.container}>
            <View style={{ height: '6.3%' }} />

            {/* --- HEADER --- */}
            <View style={styles.titleBox}>
                <Text
                    maxFontSizeMultiplier={1.2}
                    style={styles.title}
                >
                    A few{"\n"}quick{"\n"}steps to{"\n"}Nudge!&nbsp;
                    <Image
                        source={require('../assets/images/logo.png')}
                        style={styles.logo}
                    />
                </Text>
            </View>

            {/* --- LINE --- */}
            <View style={{ height: '3%' }} />
            <View style={styles.divider} />
            <View style={{ height: '3%' }} />

            {/* --- PROGRESS TRACKER --- */}
            <View style={styles.trackerBox}>
                <View style={[styles.track, { height: RING_SIZE }]}>
                    
                    {/* Layer 1: the grey rings */}
                    <View style={[styles.layer, styles.roundRow]}>
                        {granted.map((_, i) => (
                            <View
                                key={i}
                                style={{
                                    width: RING_SIZE,
                                    height: RING_SIZE,
                                    borderRadius: RING_SIZE / 2,
                                    backgroundColor: Colors.grey,
                                }}
                            />
                        ))}
                    </View>

                    {/* Layer 2: the connecting line */}
                    <View style={[styles.layer, { left: RING_SIZE / 2, right: RING_SIZE / 2, justifyContent: 'center' }]}>
                        <View style={{ height: LINE_BASE, backgroundColor: Colors.grey }} />
                        <View style={[styles.segmentRow, { height: RING_SIZE }]}>
                            <View style={{
                                flex: 1,
                                height: LINE_ACTIVE,
                                backgroundColor: hasOverlay && hasAccess ? Colors.text : 'transparent',
                            }} />
                            <View style={{
                                flex: 1,
                                height: LINE_ACTIVE,
                                backgroundColor: hasAccess && hasBattery ? Colors.text : 'transparent',
                            }} />
                        </View>
                    </View>

                    {/* Layer 3: the coloured discs */}
                    <View style={[styles.layer, styles.roundRow]}>
                        {granted.map((ok, i) => (
                            <View
                                key={i}
                                style={{ width: RING_SIZE, height: RING_SIZE, alignItems: 'center', justifyContent: 'center' }}
                            >
                                <View
                                    style={{
                                        width: DISC_SIZE,
                                        height: DISC_SIZE,
                                        borderRadius: DISC_SIZE / 2,
                                        backgroundColor: ok ? Colors.green : Colors.orange,
                                    }}
                                />
                            </View>
                        ))}
                    </View>
                </View>
            </View>

            <View style={{ height: '4.8%' }} />

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

            <View style={{ height: '5.1%' }} />

            {/* --- NUDGE BUTTON --- */}
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
                        { backgroundColor: isAllGranted ? Colors.orange : Colors.grey,
                        width: isAllGranted ? '45%' : '35%',
                        height: isAllGranted ? '100%' : '80%' }
                    ]}
                >
                    {isAllGranted && <View style={styles.pillFill} />}
                    <Text
                        numberOfLines={1}
                        adjustsFontSizeToFit
                        maxFontSizeMultiplier={1.2}
                        style={[
                            styles.pillText,
                            { color: isAllGranted ? Colors.text : '#858585',
                            fontSize: isAllGranted ? 30 : 25,
                             }
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
        height: '23.7%',
    },
    title: {
        color: Colors.text,
        fontFamily: 'WorkSans-Black',
        fontSize: 56,
        lineHeight: 62,
    },
    logo: {
        width: 54,
        height: 54,
    },
    divider: {
        height: '0.4%',
        backgroundColor: Colors.grey,
    },
    trackerBox: {
        height: '3.9%',
        justifyContent: 'center',
    },
    track: {
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