package pl.codetitans.odyssesus.testing;

import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import pl.codetitans.odyssesus.*;

public class MainActivity extends AppCompatActivity {

    private IOdysseusLog logger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        OdysseusFactory.start(this, "test-app-id", "test-app-key");
        logger = OdysseusFactory.create("MainActivity");

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        final Button addLogButton = findViewById(R.id.button_add_log);
        addLogButton.setOnClickListener(v -> {
            logger.d("Clicked a button");
        });

        final Button addEventButton = findViewById(R.id.button_add_event);
        addEventButton.setOnClickListener(v -> {
            logger.event("button-clicked");
        });
    }
}