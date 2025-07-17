package Bots.lavaplayer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public class RadioDataFetcher {

    public static String[] getStreamSongNow(String url) {
        try {
            List<String> metadata = Objects.requireNonNull(getMetadata(url));
            ArrayList<String> dataList = new ArrayList<>();
            if (!Objects.requireNonNull(metadata).get(1).isEmpty()) {
                int metaInt = Integer.parseInt(metadata.get(1));
                URL audioURL = new URL(url);
                HttpURLConnection connection = (HttpURLConnection) audioURL.openConnection();
                connection.setRequestProperty("Icy-Metadata", "1");
                connection.connect();
                InputStream inputStream = connection.getInputStream();
                for (int i = 0; i < 10; i++) {
                    inputStream.skip(metaInt);
                    String meta = readMetaData(inputStream);

                    if (meta.startsWith("StreamTitle")) {
                        String title = meta.substring("StreamTitle=".length(), meta.indexOf(';'));
                        dataList.add(title.substring(1, title.length() - 1));
                    }
                    // TODO: add track author/artist here and in LRCLIBMANAGER
                }

                connection.disconnect();
                inputStream.close();

                // If no titles found, return "Unknown title"
                if (dataList.isEmpty()) {
                    return new String[]{"Unknown title"};
                }

                return dataList.toArray(new String[0]); // Return all found titles
            } else {
                return new String[]{"Unknown title"};
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new String[]{"Unknown title"};
    }

    private static String readMetaData(InputStream stream) throws IOException {
        int length = stream.read();
        if (length < 1) {
            return "";
        }
        int metadataChunkSize = length * 16;
        byte[] metadataChunk = stream.readNBytes(metadataChunkSize);
        return new String(metadataChunk, 0, metadataChunkSize, StandardCharsets.ISO_8859_1);
    }

    public static String getStreamTitle(String url) {
        return Objects.requireNonNull(getMetadata(url)).get(0);
    }

    private static List<String> getMetadata(String url) {
        try {
            // get all generic metadata
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestProperty("Icy-Metadata", "1");
            connection.connect();

            String name = connection.getHeaderField("icy-name");
            String metaInt = connection.getHeaderField("icy-metaint");
            List<String> metadata = new ArrayList<>();

            if (name == null || name.trim().isEmpty() || name.trim().equalsIgnoreCase("null")) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line;
                int i = 0;
                while ((line = reader.readLine()) != null) {
                    i++;
                    if (i > 10) {
                        break;
                    }
                    //if (line.contains("")) // this is BAD and can fail here.
                    if (line.contains("Title1=")) {
                        name = line.split("=", 2)[1];
                        metadata.add(name);
                        break;
                    }
                }
                reader.close();
                if (name == null || name.trim().isEmpty() || name.trim().equalsIgnoreCase("null")) {
                    metadata.add((url + "/").split("//", 2)[1].split("/", 2)[0]);
                }
            } else {
                metadata.add(name);
            }
            if (metaInt == null || metaInt.trim().isEmpty() || metaInt.trim().equalsIgnoreCase("null")) {
                metadata.add("");
            } else {
                metadata.add(metaInt);
            }
            connection.disconnect();
            return metadata;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

}
