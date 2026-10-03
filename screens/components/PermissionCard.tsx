import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { Colors } from '../scripts/colors';

type PermissionCardProps = {
    title: string;
    description: string;
    isGranted: boolean;
    onPress: () => void;
    /** Slightly shorter card, used when a fourth card has to fit in the same space. */
    compact?: boolean;
};

const PermissionCard = ({ title, description, isGranted, onPress, compact }: PermissionCardProps) => {
    return (
        <View style={[styles.permissionCard, compact && styles.permissionCardCompact]}>
            <Text
                numberOfLines={1}
                adjustsFontSizeToFit
                maxFontSizeMultiplier={1.2}
                style={styles.permissionText}
            >
                {title}
            </Text>

            <Text
                numberOfLines={1}
                style={styles.permissionDescription}
            >
                {description}
            </Text>

            <TouchableOpacity
                style={[
                    styles.button,
                    { backgroundColor: isGranted ? Colors.green : Colors.orange }
                ]}
                onPress={onPress}
                disabled={isGranted}
            >
                <Text
                    maxFontSizeMultiplier={1.2}
                    style={styles.buttonText}
                >
                    {isGranted ? '✓' : 'Allow'}
                </Text>
            </TouchableOpacity>
        </View>
    );
};

const styles = StyleSheet.create({
    permissionCard: {
        height: '30%',
        backgroundColor: Colors.grey,
        paddingHorizontal: '4.3%',
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
        borderRadius: 12,
        marginBottom: 25,
    },
    permissionCardCompact: {
        height: '22%',
        marginBottom: 14,
    },
    permissionText: {
        flexShrink: 1,
        top: '-7%',
        color: Colors.text,
        fontFamily: 'WorkSans-Bold',
        fontSize: 24,
    },
    permissionDescription: {
        position: 'absolute',
        left: '5.6%',
        right: '38%',
        bottom: '7%',
        color: Colors.text,
        fontFamily: 'WorkSans-Medium',
        fontSize: 11,
    },
    button: {
        width: '36%',
        height: '52%',
        borderRadius: 999,
        alignItems: 'center',
        justifyContent: 'center',
    },
    buttonText: {
        color: Colors.text,
        fontFamily: 'WorkSans-Black',
        fontSize: 20,
    },
});

export default PermissionCard;