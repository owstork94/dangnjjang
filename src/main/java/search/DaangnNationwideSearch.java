package search;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.geometry.Insets;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class DaangnNationwideSearch extends Application {

    private TextField searchField;
    private Button searchButton;
    private ListView<String> resultList;
    private List<String> resultUrls = new ArrayList<>();
    private ComboBox<String> regionComboBox;
    private ComboBox<String> categoryComboBox;

    private String[][] regions = {
            {"가락동", "가락동-0001"}, {"가산동", "가산동-0002"}, {"가양동", "가양동-0003"},
            {"개봉동", "개봉동-0004"}, {"노형동", "노형동-3835"}, {"연동", "연동-3834"},
            {"이도2동", "이도2동-3832"}, {"아라일동", "아라일동-6995"}, {"한림읍", "한림읍-3813"},
            {"조치원읍", "조치원읍-1243"}, {"고운동", "고운동-4186"}, {"다정동", "다정동-4153"}
    };

    private final String[] categories = {"전체", "전자기기", "의류", "가구", "도서", "스포츠/레저", "기타"};

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("당근마켓 전국 검색기");

        searchField = new TextField();
        searchField.setPromptText("검색어 입력 (예: 드론)");

        Arrays.sort(regions, Comparator.comparing(a -> a[0]));

        ObservableList<String> regionList = FXCollections.observableArrayList();
        for (String[] region : regions) {
            regionList.add(region[0]);
        }

        FilteredList<String> filteredRegions = new FilteredList<>(regionList, p -> true);

        regionComboBox = new ComboBox<>(filteredRegions);
        regionComboBox.setEditable(true);
        regionComboBox.setPromptText("지역 선택");

        regionComboBox.getEditor().textProperty().addListener((obs, oldValue, newValue) -> {
            if (regionComboBox.isShowing()) {
                return; // 드롭다운 펼쳐진 상태에서는 필터링하지 않음
            }
            if (newValue == null || newValue.isEmpty()) {
                filteredRegions.setPredicate(p -> true);
            } else {
                final String lowerCaseFilter = newValue.toLowerCase();
                filteredRegions.setPredicate(item -> {
                    if (item == null) return false;
                    return item.toLowerCase().contains(lowerCaseFilter);
                });
            }
        });

        categoryComboBox = new ComboBox<>(FXCollections.observableArrayList(categories));
        categoryComboBox.setValue("전체");

        searchButton = new Button("검색");
        searchButton.setOnAction(e -> searchItems());

        resultList = new ListView<>();
        resultList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                int index = resultList.getSelectionModel().getSelectedIndex();
                if (index >= 0 && index < resultUrls.size()) {
                    openWebpage(resultUrls.get(index));
                }
            }
        });

        VBox layout = new VBox(10);
        layout.setPadding(new Insets(15));
        layout.getChildren().addAll(searchField, regionComboBox, categoryComboBox, searchButton, resultList);

        Scene scene = new Scene(layout, 600, 450);
        primaryStage.setScene(scene);
        primaryStage.show();

        System.out.println("[INFO] 애플리케이션 시작 완료");
    }

    private void searchItems() {
        String keyword = searchField.getText().trim();
        String selectedRegion = regionComboBox.getValue();
        String selectedCategory = categoryComboBox.getValue();
        if (keyword.isEmpty() || selectedRegion == null) {
            System.out.println("[WARN] 검색어 또는 지역이 비어있습니다.");
            return;
        }

        System.out.println("[INFO] 검색어: " + keyword);
        System.out.println("[INFO] 선택된 지역: " + selectedRegion);
        System.out.println("[INFO] 선택된 카테고리: " + selectedCategory);

        resultList.getItems().clear();
        resultUrls.clear();

        String areaCode = null;
        for (String[] region : regions) {
            if (region[0].equals(selectedRegion)) {
                areaCode = region[1];
                break;
            }
        }

        if (areaCode == null) {
            System.out.println("[ERROR] 지역 코드 매칭 실패");
            return;
        }

        String url = "https://www.daangn.com/kr/buy-sell/?in=" +
                URLEncoder.encode(areaCode, StandardCharsets.UTF_8) +
                "&only_on_sale=true&search=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);

        if (!"전체".equals(selectedCategory)) {
            url += "&category=" + URLEncoder.encode(selectedCategory, StandardCharsets.UTF_8);
        }

        final String searchUrl = url;

        System.out.println("[INFO] 검색 URL: " + searchUrl);

        new Thread(() -> {
            try {
                System.out.println("[INFO] 크롤링 시작");
                Document doc = Jsoup.connect(searchUrl).get();
                Elements items = doc.select(".flea-market-article-link");

                if (items.isEmpty()) {
                    System.out.println("[INFO] 검색 결과 없음");
                }

                for (Element item : items) {
                    String title = item.select(".article-title").text();
                    String price = item.select(".article-price").text();
                    String regionName = item.select(".article-region-name").text();
                    String itemUrl = "https://www.daangn.com" + item.attr("href");

                    String result = title + " / " + price + " / " + regionName;

                    final String displayResult = result;
                    final String urlToSave = itemUrl;

                    javafx.application.Platform.runLater(() -> {
                        resultList.getItems().add(displayResult);
                        resultUrls.add(urlToSave);
                    });
                }

                System.out.println("[INFO] 크롤링 완료. 결과 수: " + items.size());

                javafx.application.Platform.runLater(() -> openWebpage(searchUrl));

            } catch (IOException e) {
                System.out.println("[ERROR] 크롤링 중 오류 발생");
                e.printStackTrace();
            }
        }).start();
    }

    private void openWebpage(String url) {
        try {
            System.out.println("[INFO] 브라우저 열기: " + url);
            Desktop.getDesktop().browse(URI.create(url));
        } catch (IOException e) {
            System.out.println("[ERROR] 브라우저 열기 실패");
            e.printStackTrace();
        }
    }
}
