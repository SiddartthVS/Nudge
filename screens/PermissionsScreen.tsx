import React from 'react';
import { View,Text,TouchableOpacity,StyleSheet} from 'react-native';
import { NativeModules } from 'react-native';
import { Colors } from './colors';
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
            
            <View style={styles.track}>
                <View style={[styles.trackRound,
                            { left:'0%' }]}></View>

                <View style={[styles.trackRound,
                            { left:'30%' }]}></View>

                <View style={[styles.trackRound,
                            { width: '40%', left: '60%' }]}>
                                <Text style={styles.trackText}>Nudge!-></Text>
                            </View>
                
                <View style={styles.trackLine}></View>
            </View>

            <View style={styles.permissionCard}>
                <Text style={styles.permissionText}>
                    Display over{"\n"}apps
                </Text>

                <TouchableOpacity
                    style={styles.button}
                    onPress={handleOverlayPermission}
                >
                    <Text style={styles.buttonText}>
                        Allow
                    </Text>
                </TouchableOpacity>
            </View>

            <View style={styles.permissionCard}>
                <Text style={styles.permissionText}>
                    Accessibility{"\n"}service
                </Text>

                <TouchableOpacity
                    style={styles.button}
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
        backgroundColor: Colors.background,
        padding: 16,
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
        backgroundColor: Colors.grey,
        zIndex: 1,
    },

    trackText: {
        color: Colors.text,
        fontSize: 20,
        fontFamily: 'WorkSans-Black',
        lineHeight: 40,
        textAlign: 'center',
   },

    trackLine: {
        position: 'absolute',
        justifyContent: 'center',
        alignItems: 'center',
        top: 15,
        width: '100%',
        height: '10%',
        backgroundColor: Colors.grey,
        zIndex: 0,
    },

    permissionCard: {
        height: '10%',
        backgroundColor: Colors.grey,
        borderRadius: 7,
        marginBottom: 30,
        paddingHorizontal: 13,
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
    },

    permissionText: {
        color: Colors.text,
        fontSize: 24,
        fontFamily: 'WorkSans-Bold',
    },

    button: {
        backgroundColor: Colors.orange,
        width: '30%',
        height: '50%',
        borderRadius: 40,
        alignItems: 'center',
        justifyContent: 'center',
    },

    buttonText: {
        color: Colors.text,
        fontSize: 20,
        fontFamily: 'WorkSans-Black',
    },

});

export default PermissionsScreen;