package com.auto.process;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import com.auto.process.annotation.Exclusive;
import com.auto.process.api.ViewProcessHelper;

public class MainActivity extends AppCompatActivity {

    @Exclusive(R.id.tv)
    TextView textView;

    @Exclusive(R.id.imageView)
    ImageView imageView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ViewProcessHelper.bind(this);
        Log.d("","");
    }

    @Override
    protected void onResume() {
        super.onResume();
        //textView.setText("this is auto generate reference.");
    }
}
