import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { Colors } from '../colors';
import { useBase, pct } from '../scale';

type PermissionCardProps = {
    title: string;
    description: string;
    isGranted: boolean;
    onPress: () => void;
};

/**
 * One permission row: title on the left (vertically centred with the button), a small hint line
 * underneath it, and the Allow / tick button on the right.
 *
 * Every size here is a percentage: the card is a % of its parent's height (set by the screen),
 * padding / button / hint position are % of the card, and font sizes are % of the screen via
 * pct(). Measured from the mockup.
 */
const PermissionCard = ({ title, description, isGranted, onPress }: PermissionCardProps) => {
    const base = useBase();

    return (
        <View style={[styles.permissionCard, { borderRadius: pct(base, 2.4) }]}>
            <Text
                numberOfLines={1}
                adjustsFontSizeToFit
                maxFontSizeMultiplier={1.2}
                style={[styles.permissionText, { fontSize: pct(base, 4.8) }]}
            >
                {title}
            </Text>

            {/* Mockup hint text is ~2.4% of the width, which is only ~9sp on a phone, so it is
                floored at 10 to stay readable. Change 3 -> 2.4 to match the mockup exactly. */}
            <Text
                numberOfLines={1}
                style={[styles.permissionDescription, { fontSize: Math.max(pct(base, 2.6), 10) }]}
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
                    style={[styles.buttonText, { fontSize: pct(base, 5.2) }]}
                >
                    {isGranted ? '✓' : 'Allow'}
                </Text>
            </TouchableOpacity>
        </View>
    );
};

const styles = StyleSheet.create({
    permissionCard: {
        // 27.7% of the cards block (which is 33.4% of the screen height) = 9.25% of the screen.
        height: '27.7%',
        backgroundColor: Colors.grey,
        paddingHorizontal: '4.3%',
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
    },
    permissionText: {
        flexShrink: 1,
        top: '-7%',
        color: Colors.text,
        fontFamily: 'WorkSans-Bold',
    },
    permissionDescription: {
        position: 'absolute',
        left: '5.6%',
        // Stops before the button (button 33% + right padding 4.3%) so it can never run under it.
        right: '38%',
        bottom: '7%',
        color: Colors.text,
        fontFamily: 'WorkSans-Medium',
    },
    button: {
        // Percentages are of the card's inner width/height: 36% of the inner width is 33% of the card.
        width: '36%',
        height: '52%',
        borderRadius: 999,
        alignItems: 'center',
        justifyContent: 'center',
    },
    buttonText: {
        color: Colors.text,
        fontFamily: 'WorkSans-Black',
    },
});

export default PermissionCard;
