package com.test.supportfragment;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;


public class MainFragment extends Fragment implements OnClickListener {

    private Activity activity;
    private View view;
    private Button btn;

    public MainFragment() {

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
			     Bundle savedInstanceState) {
	// TODO: can't handle these yet
	//this.activity = getActivity();
	// will be a null dispatch if we don't know who our parent is
	//activity.toString(); 
	this.view = inflater.inflate(R.layout.fragment, container);
	initView(view);
	return view;
    }

    private void initView(View v) {
	this.btn = (Button) view.findViewById(R.id.myBtn);
	this.btn.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
	int j = 0;
	// this will be a bogus branch if we don't know that v and
	// btn can be aliased
	if (v == btn) {
	    j++;
	}
    }
}