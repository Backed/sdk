package com.android.tests.basiclibwithjar;

import com.android.tests.basicjar.Foo;

import android.app.Activity;
import android.os.Bundle;

public class Main extends Activity
{
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Foo foo = new Foo();
        int a = foo.getRandomFoo();

    }
}