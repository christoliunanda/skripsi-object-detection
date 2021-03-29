package org.tensorflow.lite.examples.detection;

import android.os.Bundle;
import android.os.PersistableBundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import org.tensorflow.lite.examples.detection.env.Logger;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

public class settingActivity extends AppCompatActivity{
    private static final String FILE_NAME="setting.txt";

    EditText mEmailText,mTelpText;
    Spinner spinner;

//    @Nullable
//    @Override
//    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
//        return inflater.inflate(R.layout.fragment_home,container,false);
//    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);


        mEmailText = findViewById(R.id.editTextEmail);
        mTelpText = findViewById(R.id.editTextTelp);

        spinner = findViewById(R.id.spinner1);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,R.array.numbers,android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        load_setting();
    }

    public void save_setting(View view) {
        FileOutputStream fos = null;

        String text = spinner.getSelectedItem().toString()+"\n";
        text = text + mTelpText.getText().toString()+"\n"+ mEmailText.getText().toString()+"\n";

        try {
            fos = openFileOutput(FILE_NAME,MODE_PRIVATE);
            fos.write(text.getBytes());

            Toast.makeText(this, "Saved to "+getFilesDir()+"/"+FILE_NAME, Toast.LENGTH_SHORT).show();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(fos != null){
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void load_setting(){
        FileInputStream fis = null;
        ArrayList<String> fullText = new ArrayList<>();
        try{
            fis=openFileInput(FILE_NAME);
            InputStreamReader isr = new InputStreamReader(fis);
            BufferedReader br = new BufferedReader(isr);

            String text;
            while((text = br.readLine()) != null){

                fullText.add(text);
            }
            String modelName= fullText.get(0);
            spinner.setSelection(getIndex(spinner,modelName));
            mTelpText.setText(fullText.get(1));
            mEmailText.setText(fullText.get(2));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(fis != null){
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private int getIndex(Spinner spinner, String modelName) {
        int index = 0;
        for (int i=0;i<spinner.getCount();i++){
            if (spinner.getItemAtPosition(i).equals(modelName)){
                index = i;
            }
        }
        return index;
    }


}
