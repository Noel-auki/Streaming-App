import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() => runApp(MyApp());

class MyApp extends StatelessWidget {
  static const platform = MethodChannel('com.stream_ivs/ivs');

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        scaffoldBackgroundColor: Color(0xFF252525), // Set background color
        primarySwatch: Colors.red,
        elevatedButtonTheme: ElevatedButtonThemeData(
          style: ElevatedButton.styleFrom(
            foregroundColor: Colors.white,
            backgroundColor:
                Color(0xFFC7315C), // Set button text color to white
            shape: RoundedRectangleBorder(
              borderRadius:
                  BorderRadius.circular(8), // Rounded corners for buttons
            ),
            padding: EdgeInsets.symmetric(
                horizontal: 20, vertical: 15), // Button padding
          ),
        ),
        appBarTheme: const AppBarTheme(
          backgroundColor: Color(0xFF252525),
          titleTextStyle: TextStyle(
            color: Colors.white, // Set title text color to white
            fontWeight: FontWeight.bold, // Make title text bold
            fontSize: 20.0, // Set title text font size to default (20.0)
          ),
        ),
      ),
      home: Scaffold(
        appBar: AppBar(
          title: Text("Stream IVS"),
        ),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: <Widget>[
              ElevatedButton(
                onPressed: () => platform.invokeMethod('startStream'),
                child: Text('Start Live Stream'),
              ),
              SizedBox(height: 20), // Add space between buttons
              ElevatedButton(
                onPressed: () => platform.invokeMethod('watchStream'),
                child: Text('Watch Live Stream'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
