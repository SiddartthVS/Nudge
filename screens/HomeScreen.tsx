import { StyleSheet, Text, View } from "react-native";
import { Colors } from "./scripts/colors";
import CountDisplay  from "./components/CountDisplay";
import { AppStats } from "./components/AppStats";
import { WeekStats } from "./components/WeekStats";

const HomeScreen = () => {
    return (
        <View style={Styles.container}>
            <Text>Welcome to the Home Screen!</Text>
            <View style={Styles.countDisplay}><CountDisplay /></View>
            <View style={Styles.weekStats}><WeekStats /></View>
            <View style={Styles.appStats}><AppStats /></View>
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
    countDisplay: {
        top: '5%',
        height: '36%',
        width: '85%',
    },
    weekStats: {
        top: '8%',
        height: '22.5%',
        width: '85%',
    },
    appStats: {
        top: '8%',
        height:'22.5%',
        width: '85%',
    },
    settings: {
        top: '8%',
        height: '5%',
        width: '10%',
        backgroundColor: Colors.grey,
    },
});
export default HomeScreen;