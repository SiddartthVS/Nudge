import { StyleSheet, Text, View } from "react-native";
import { Colors } from "./scripts/colors";
import { MONITORED_APPS } from "./scripts/chart";
import CountDisplay  from "./components/CountDisplay";
import { AppStats } from "./components/AppStats";
import { WeekStats } from "./components/WeekStats";
import Blockbutton from "./components/Blockbutton";

const instagram = MONITORED_APPS.find(app => app.pkg === 'com.instagram.android')!;
const youtube = MONITORED_APPS.find(app => app.pkg === 'com.google.android.youtube')!;

const HomeScreen = () => {
    return (
        <View style={Styles.container}>
            <Text>Welcome to the Home Screen!</Text>
            <View style={Styles.countDisplay}><CountDisplay /></View>
            <View style={Styles.weekStats}><WeekStats /></View>
            <View style={Styles.appStats}><AppStats /></View>
            <View style={Styles.blockRow}>
                <Blockbutton label={instagram.label} pkg={instagram.pkg} />
                <Blockbutton label={youtube.label} pkg={youtube.pkg} />
            </View>
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
    blockRow: {
        top: '10%',
        height: '8%',
        width: '85%',
        flexDirection: 'row',
        gap: 12,
    },
    settings: {
        top: '8%',
        height: '5%',
        width: '10%',
        backgroundColor: Colors.grey,
    },
});
export default HomeScreen;