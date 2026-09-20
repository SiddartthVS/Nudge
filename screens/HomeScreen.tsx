import { StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { Colors } from "./colors";
import CountDisplay  from "./components/CountDisplay";
import { AppStats } from "./components/AppStats";
import { WeekStats } from "./components/WeekStats";

const HomeScreen = () => {
    return (
        <View style={Styles.container}>
            <Text>Welcome to the Home Screen!</Text>
            <View style={Styles.topSpace} />
            <View style={Styles.countDisplay}><CountDisplay /></View>
            <View style={Styles.weekStats}><WeekStats /></View>
            <View style={Styles.appStats}><AppStats /></View>
            <TouchableOpacity style={Styles.settings}></TouchableOpacity>
        </View>
    );
};

const Styles = StyleSheet.create({
    container: {
        justifyContent: 'flex-start',
        alignItems: 'center',
        display: 'flex',
        flex: 1,
        backgroundColor: Colors.background,
    },
    // Mockup: the count card starts 8% down the screen and is 36% tall. `top` is not used here
    // because it only shifts the card visually without reserving space, which is what made it
    // slide down over the "This week" card. A real spacer above it does the job instead.
    topSpace: {
        height: '8%',
    },
    countDisplay: {
        height: '36%',
        width: '90%',
    },
    weekStats: {
        height: '30%',
        width: '90%',
    },
    appStats: {
        height: '30%',
        width: '90%',
    },
    settings: {
        height: '10%',
        width: '10%',
        backgroundColor: Colors.grey,
    },
});
export default HomeScreen;