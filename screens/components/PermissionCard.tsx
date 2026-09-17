import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { Colors } from '../colors';

const PermissionCard = ({ title, isGranted, onPress }) => (
    <View style={styles.permissionCard}>
        <Text style={styles.permissionText}>{title}</Text>
        <TouchableOpacity
            style={[
                styles.button,
                isGranted ? { backgroundColor: Colors.green } : { backgroundColor: Colors.orange }
            ]}
            onPress={onPress}
            disabled={isGranted}
        >
            <Text style={styles.buttonText}>
                {isGranted ? '✓' : 'Allow'}
            </Text>
        </TouchableOpacity>
    </View>
);

const styles = StyleSheet.create({
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

export default PermissionCard;