import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, Image, AppState, TouchableOpacity } from 'react-native';
import { NativeModules } from 'react-native';
import { Colors } from './colors';
import PermissionCard from './components/PermissionCard';

const { PermissionsModule: pm } = NativeModules;

const PermissionsScreen = ({ onComplete }) => {
    const [hasOverlay, setHasOverlay] = useState(false);
    const [hasAccess, setHasAccess] = useState(false);

    const checkPermissions = async () => {
        try {
            const overlayStatus = await pm.checkOverlayPermission();
            const accessStatus = await pm.checkAccessibilityPermission();
            
            setHasOverlay(overlayStatus);
            setHasAccess(accessStatus);
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

    const isAllGranted = hasOverlay && hasAccess;

    return (
        <View style={styles.container}>
            {/* --- HEADER --- */}
            <Text style={styles.title}>
                A few{"\n"}quick{"\n"}steps to{"\n"}Nudge!&nbsp;
                <Image
                    source={require('../assets/images/logo.png')}
                    style={styles.logo}
                />
            </Text>

            <View style={styles.line} />
            
            {/* --- PROGRESS TRACKER --- */}
            <View style={styles.track}>
                {/* Step 1 Node */}
                <View style={[
                    styles.trackRound,
                    { left: '0%' },
                    hasOverlay 
                        ? { backgroundColor: Colors.green, boxShadow: "inset 0 0 0 4px " + Colors.text } 
                        : { backgroundColor: Colors.orange, boxShadow: "inset 0 0 0 10px " + Colors.grey }
                ]}></View>

                {/* Step 2 Node */}
                <View style={[
                    styles.trackRound,
                    { left: '30%' },
                    hasAccess 
                        ? { backgroundColor: Colors.green, boxShadow: "inset 0 0 0 4px " + Colors.text } 
                        : { backgroundColor: Colors.orange, boxShadow: "inset 0 0 0 10px " + Colors.grey }
                ]}></View>

                {/* Final Pill Node */}
                <View style={[
                    styles.trackRound,
                    { width: '40%', left: '60%', backgroundColor: Colors.grey }
                ]}>
                    <TouchableOpacity 
                        activeOpacity={isAllGranted ? 0.7 : 1}
                        onPress={() => {
                            if (isAllGranted) {
                                onComplete();
                            }
                        }}
                    >
                        <Text style={[
                            styles.trackText,
                            isAllGranted ? styles.trackPillEnabled : styles.trackPillDisabled
                        ]}>
                            Nudge!➜
                        </Text>
                    </TouchableOpacity>
                </View>

                {/* Connecting Lines */}
                <View style={styles.trackLineBase} />
                <View style={[
                    styles.trackLineActive,
                    {
                        backgroundColor: Colors.text,
                        height: '5%',
                        left: '2%',
                        right: '2%',
                        width: isAllGranted ? '90%' : hasOverlay || hasAccess ? '30%' : '0%'
                    }
                ]} />
            </View>

            {/* --- PERMISSION CARDS --- */}
            <PermissionCard
                title={`Display over\napps`}
                isGranted={hasOverlay}
                onPress={() => pm.requestOverlayPermission()}
            />

            <PermissionCard
                title={`Accessibility\nservice`}
                isGranted={hasAccess}
                onPress={() => pm.requestAccessibilityPermission()}
            />

        </View>
    );
};

const styles = StyleSheet.create({
    container: {
        flex: 1,
        backgroundColor: Colors.background,
        padding: 16,
    },
    logo: {
        width: 50,
        height: 50,
        backgroundColor: 'transparent',
        alignSelf: 'center',
    },
    title: {
        color: Colors.text,
        fontSize: 60,
        marginTop: '20%',
        fontFamily: 'WorkSans-Black',
    },
    line: {
        height: 2,
        backgroundColor: Colors.grey,
        marginTop: 25,
        marginBottom: 30,
    },
    track: {
        top: 10,
        height: '10%',
        width: '85%',
        alignSelf: 'center',
    },
    trackRound: {
        width: 40,
        height: 40,
        position: 'absolute',
        borderRadius: 20,
        zIndex: 1,
    },
    trackText: {
        fontFamily: 'WorkSans-Black',
        lineHeight: 40,
        textAlign: 'center',
    },
    trackLineBase: {
        position: 'absolute',
        justifyContent: 'center',
        alignItems: 'center',
        top: 15,
        width: '100%',
        height: '5%',
        backgroundColor: Colors.grey,
        zIndex: 0,
    },
    trackLineActive: {
        position: 'absolute',
        top: 15,
        zIndex: 0,
    },
    trackPillEnabled: {
        fontSize: 24,
        color: Colors.text,
        backgroundColor: Colors.orange,
        borderRadius: 20,
        boxShadow: "inset 60px 0 0 0px " + Colors.green + ",0 0 0 4px " + Colors.text
    },
    trackPillDisabled: {
        fontSize: 20,
        color: '#858585',
    },
});

export default PermissionsScreen;