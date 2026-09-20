import { useWindowDimensions } from 'react-native';

/**
 * Screen scaling for the permissions UI.
 *
 * The mockup was drawn on a 293 x 670 canvas. Instead of hard-coding sizes, everything is a
 * PERCENTAGE of that canvas, so the screen looks the same on any phone:
 *
 *  - vertical layout (heights of the sections) uses '%' of the screen height directly in styles
 *  - anything that cannot take a '%' in React Native (font sizes, circle sizes, line thickness)
 *    is computed here as a percentage of `base`
 *
 * `base` is the width of the biggest 293:670 canvas that fits on this screen. On a phone with
 * the same proportions as the mockup it is simply the screen width. On a shorter/wider phone it
 * is limited by the height instead, so text and circles shrink rather than running off the
 * screen. That "whichever is smaller" rule is what stops the UI from overflowing.
 */
const DESIGN_WIDTH = 293;
const DESIGN_HEIGHT = 670;

export const useBase = (): number => {
    const { width, height } = useWindowDimensions();
    return Math.min(width, (height * DESIGN_WIDTH) / DESIGN_HEIGHT);
};

/** p percent of `base`. e.g. pct(base, 5) is 5% of the design width. */
export const pct = (base: number, p: number): number => (base * p) / 100;
