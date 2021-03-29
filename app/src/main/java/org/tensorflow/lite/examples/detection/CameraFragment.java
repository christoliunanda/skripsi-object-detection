package org.tensorflow.lite.examples.detection;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class CameraFragment extends Fragment {
    private Button photoButton,videoButton;
    private ImageView imageView;

    static final int REQUEST_IMAGE_CAPTURE = 1;
    public static final int RESULT_OK = -1;

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getActivity().getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }



    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragments_camera,container,false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        photoButton = getView().findViewById(R.id.button_photo);
        videoButton = getView().findViewById(R.id.button_video);
        imageView = getView().findViewById(R.id.imageViewTemp);

        videoButton.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(),DetectorActivity.class);
            startActivity(intent);

        });

        photoButton.setOnClickListener(v->{
            dispatchTakePictureIntent();
        });

    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");
            //imageView.setImageBitmap(imageBitmap);

            Intent intent = new Intent(getActivity(),DetectImageFromCamera.class);
            intent.putExtra("cameraImageBitmap",imageBitmap);
            startActivity(intent);
        }
    }

}
