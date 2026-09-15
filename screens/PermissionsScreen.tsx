import React from 'react';
import {
    View,
    Text,
    TouchableOpacity,
    StyleSheet,
} from 'react-native';

import { NativeModules } from 'react-native';

const { PermissionsModule } = NativeModules;

const PermissionsScreen = () => {

    const handleOverlayPermission = () => {
        PermissionsModule.requestOverlayPermission();
    };

    const handleAccessibilityPermission = () => {
        PermissionsModule.requestAccessibilityPermission();
    };

    return (
        <View style={styles.container}>

            <Text style={styles.title}>
                A few{"\n"}
                quick{"\n"}
                steps to{"\n"}
                Nudge!
            </Text>

            <View style={styles.line} />

            {/* Overlay Permission */}
            <View style={styles.permissionCard}>
                <Text style={styles.permissionText}>
                    Display over{"\n"}apps
                </Text>

                <TouchableOpacity
                    style={styles.allowButton}
                    onPress={handleOverlayPermission}
                >
                    <Text style={styles.buttonText}>
                        Allow
                    </Text>
                </TouchableOpacity>
            </View>

            {/* Accessibility Permission */}
            <View style={styles.permissionCard}>
                <Text style={styles.permissionText}>
                    Accessibility{"\n"}service
                </Text>

                <TouchableOpacity
                    style={styles.allowButton}
                    onPress={handleAccessibilityPermission}
                >
                    <Text style={styles.buttonText}>
                        Allow
                    </Text>
                </TouchableOpacity>
            </View>

        </View>
    );
};

const styles = StyleSheet.create({

    container: {
        flex: 1,
        backgroundColor: '#111111',
        padding: 16,
    },

    title: {
        color: '#FFFFFF',
        fontSize: 36,
        fontWeight: '900',
        marginTop: 45,
    },

    line: {
        height: 2,
        backgroundColor: '#333333',
        marginTop: 25,
        marginBottom: 30,
    },

    permissionCard: {
        height: 64,
        backgroundColor: '#3A3A3A',
        borderRadius: 7,
        marginBottom: 30,
        paddingHorizontal: 13,
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
    },

    permissionText: {
        color: '#FFFFFF',
        fontSize: 16,
        fontWeight: '700',
    },

    allowButton: {
        backgroundColor: '#FF6B70',
        width: 84,
        height: 36,
        borderRadius: 20,
        alignItems: 'center',
        justifyContent: 'center',
    },

    buttonText: {
        color: '#FFFFFF',
        fontSize: 15,
        fontWeight: '800',
    },

});

export default PermissionsScreen;