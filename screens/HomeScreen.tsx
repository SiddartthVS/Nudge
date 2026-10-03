import { useState } from "react";
import { StyleSheet, Text, View } from "react-native";
import { Colors } from "./scripts/colors";
import { MONITORED_APPS } from "./scripts/chart";
import CountDisplay  from "./components/CountDisplay";
import { AppStats } from "./components/AppStats";
import { WeekStats } from "./components/WeekStats";
import Blockbutton from "./components/Blockbutton";
import Unblocking, { UnblockingMode } from "./BlockingScreen";

const instagram = MONITORED_APPS.find(app => app.pkg === 'com.instagram.android')!;
const youtube = MONITORED_APPS.find(app => app.pkg === 'com.google.android.youtube')!;

const HomeScreen = () => {
    const [blockTarget, setBlockTarget] = useState<{ label: string; pkg: string; mode: UnblockingMode } | null>(null);

    if (blockTarget) {
        return <Unblocking {...blockTarget} onClose={() => setBlockTarget(null)} />;
    }

    return (
        <View style={Styles.container}>
            <View style={Styles.countDisplay}><CountDisplay /></View>
            <View style={Styles.weekStats}><WeekStats /></View>
            <View style={Styles.appStats}><AppStats /></View>
            <View style={Styles.blockRow}>
                <Blockbutton
                    label={instagram.label}
                    pkg={instagram.pkg}
                    onPress={mode => setBlockTarget({ label: instagram.label, pkg: instagram.pkg, mode })}
                />
                <Blockbutton
                    label={youtube.label}
                    pkg={youtube.pkg}
                    onPress={mode => setBlockTarget({ label: youtube.label, pkg: youtube.pkg, mode })}
                />
            </View>
        </View>
    );
};

const Styles = StyleSheet.create({
    container: {
        flex: 1,
        alignItems: 'center',
        backgroundColor: Colors.background,
        paddingTop: '10%',
        paddingBottom: '8%',
    },
    countDisplay: {
        width: '85%',
        flex: 1,
        marginBottom: 16,
    },
    weekStats: {
        width: '85%',
        marginBottom: 16,
    },
    appStats: {
        width: '85%',
        marginBottom: 16,
    },
    blockRow: {
        width: '85%',
        height: 60,
        marginBottom:'8%',
        flexDirection: 'row',
        gap: 12,
    },
});
export default HomeScreen;