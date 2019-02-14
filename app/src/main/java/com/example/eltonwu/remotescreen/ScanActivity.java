package com.example.eltonwu.remotescreen;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.net.InetAddress;
import java.util.ArrayList;

public class ScanActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {
    private ListView mListView;
    private ProgressDialog mProgressDialog;
    private ArrayList<InetAddress> mIPAddress = new ArrayList<>();
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        mListView = findViewById(R.id.remoteList);
        mListView.setOnItemClickListener(this);
    }

    private void performScan(){
        if(mProgressDialog != null ){
            if(!mProgressDialog.isShowing()){
                new ScanTask().execute();
            }
        }else{
            new ScanTask().execute();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        performScan();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.scan_action_menu,menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.action_scan){
            performScan();
        }
        return true;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        String addr = (String) parent.getItemAtPosition(position);
        Intent intent = new Intent(this,MainActivity.class);
        intent.putExtra("ipaddr",addr);
        startActivity(intent);
    }

    private class ScanTask extends AsyncTask<Void,Void,Void>{

        @Override
        protected Void doInBackground(Void... voids) {
            NetUtils.scanNetwork(mIPAddress);
            return null;
        }

        @Override
        protected void onPreExecute() {
            mProgressDialog = ProgressDialog.show(ScanActivity.this,null,"Scaning...",true,false);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if(mProgressDialog != null ){
                if(mProgressDialog.isShowing()){
                   mProgressDialog.dismiss();
                }
            }

            IPAddressAdapter adapter = new IPAddressAdapter(mIPAddress);
            mListView.setAdapter(adapter);
        }
    }

    private class IPAddressAdapter extends BaseAdapter {
        private ArrayList<InetAddress> mIPAddress;

        public IPAddressAdapter(ArrayList<InetAddress> addresses) {
            this.mIPAddress = addresses;
        }

        @Override
        public int getCount() {
            return mIPAddress.size();
        }

        @Override
        public Object getItem(int position) {
            return mIPAddress.get(position).getHostAddress();
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            InetAddress address = mIPAddress.get(position);
            if(convertView == null){
                convertView = getLayoutInflater().inflate(android.R.layout.simple_list_item_1,null);
            }
            if(!(convertView instanceof TextView)){
                convertView = getLayoutInflater().inflate(android.R.layout.simple_list_item_1,null);
            }
            TextView tv = (TextView) convertView;
            tv.setText(address.getHostAddress());

            return convertView;
        }
    }
}
