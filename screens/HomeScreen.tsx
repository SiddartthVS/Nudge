import { StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { Colors } from "./scripts/colors";
import CountDisplay  from "./components/CountDisplay";
import { AppStats } from "./components/AppStats";
import { WeekStats } from "./components/WeekStats";

const HomeScreen = () => {
    return (
        <View style={Styles.container}>
            <Text>Welcome to the Home Screen!</Text>
            <View style={Styles.topSpace} />
            <View style={Styles.countDisplay}><CountDisplay /></View>
            <View style={Styles.countGap} />
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
    topSpace: {
        height: '8%',
    },
    countDisplay: {
        height: '36%',
        width: '85%',
    },
    countGap: {
        height: '5.4%',
    },
    weekStats: {
        height: '30%',
        width: '85%',
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