import { Text, View } from "react-native";

const HomeScreen = () => {
    return (
        <View style={Styles.container}>
            <Text style={Styles.title}>Welcome to the Home Screen!</Text>
        </View>
    );
};

const Styles = {
    container: {
        flex: 1,
        justifyContent: 'center',
        alignItems: 'center',
    },
    title: {
        fontSize: 24,
        color: '#eeeeee',
        fontFamily: 'WorkSans-Black',
    },
};

export default HomeScreen;