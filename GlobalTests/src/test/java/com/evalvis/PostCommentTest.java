package com.evalvis;

import au.com.origin.snapshots.Expect;
import au.com.origin.snapshots.annotations.SnapshotName;
import au.com.origin.snapshots.junit5.SnapshotExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.node.ArrayNode;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.node.ObjectNode;
import protobufs.CommentRequest;
import protobufs.PostRequest;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static io.restassured.RestAssured.*;
import static shadow.org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Testcontainers
@ExtendWith({SnapshotExtension.class})
public class PostCommentTest {

    private Expect expect;

    @Container
    private static final DockerComposeContainer<?> dockerComposeContainer =
            new DockerComposeContainer<>(
                    new File("src/test/resources/docker-compose-test.yaml")
            )
                    .withExposedService("postgres", 5432)
                    .withExposedService("mongodb", 27017)
                    .withExposedService("blog", 8080)
                    .withExposedService("post", 8080);

    @Test
    @SnapshotName("createsPostWithComments")
    public void createsPostWithComments() throws IOException {
        PostRequest postRequest = PostRequest
                .newBuilder()
                .setAuthor("Human")
                .setTitle("Testing matters")
                .setContent("You either test first, test along coding, or don't test at all.")
                .build();

        String postId = given()
                .baseUri("http://localhost:8081")
                .body(postRequest.toString())
                .when()
                .post("/posts/create")
                .getBody()
                .jsonPath()
                .get("id")
                .toString();
        int commentCount = 2;
        String[] commentIds = new String[commentCount];
        for(int i = 0; i < commentCount; i++) {
            commentIds[i] = given()
                    .baseUri("http://localhost:8080")
                    .body(
                            CommentRequest
                                    .newBuilder()
                                    .setAuthor("author" + i)
                                    .setContent("content" + i)
                                    .setPostId(postId)
                            .build().toString()
                    )
                    .post("/comments/create")
                    .getBody()
                    .jsonPath()
                    .get("id");
        }
        ArrayNode comments = (ArrayNode) new ObjectMapper().readTree(
                get("http://localhost:8080/comments/list-comments/" + postId)
                        .getBody()
                        .asString()
        );
        assertThat(comments.size()).isEqualTo(commentCount);
        for(int i = 0; i < comments.size(); i++) {
            assertThat(comments.get(i).get("id").textValue()).isEqualTo(commentIds[i]);
        }
        maskProperties(comments, "id", "postEntryId");
        expect.toMatchSnapshot(comments.toString());
    }

    private void maskProperties(ArrayNode node, String... properties) {
        node.forEach(element ->
                Arrays
                        .stream(properties)
                        .forEach(property -> ((ObjectNode) element).put(property, "#hidden#"))
        );
    }
}