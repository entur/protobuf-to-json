package no.entur.protobuf.json.test;

import com.google.protobuf.ApiProto;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Field;
import com.google.protobuf.Message;
import com.google.protobuf.SourceContext;
import com.google.protobuf.StructProto;
import com.google.protobuf.Syntax;
import com.google.protobuf.Timestamp;
import com.google.protobuf.TypeProto;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3;
import no.entur.protobuf.json.test.protobufjackson.GithubApi.SearchResponse;
import no.entur.protobuf.json.test.protobufjackson.GithubApi.User;

import java.util.List;

/**
 * Benchmark payloads, built in memory. The lite benchmarks and tests get them as files, see {@linkplain PayloadFiles}.
 */

public class BenchmarkPayloads {

    public static final List<String> NAMES = List.of("small", "medium", "large", "xlarge", "xxlarge");

    // about 128 KB and 256 KB as JSON
    private static final int XLARGE_REPOSITORIES = 26;
    private static final int XXLARGE_REPOSITORIES = 52;

    /**
     * @param name small, medium or large
     * @return message
     */

    public static Message create(String name) {
        return switch (name) {
            // typical small request
            case "small" -> TestAllTypesProto3.newBuilder()
                    .setOptionalString("Hello world")
                    .setOptionalInt32(42)
                    .setOptionalInt64(1234567890123L)
                    .setOptionalNestedEnum(TestAllTypesProto3.NestedEnum.BAR)
                    .setOptionalTimestamp(Timestamp.newBuilder().setSeconds(1700000000).build())
                    .setOptionalNestedMessage(TestAllTypesProto3.NestedMessage.newBuilder().setA(1))
                    .build();
            // a typical API response: strings (mostly URLs), numbers, booleans, enums, timestamps and nested messages
            case "medium" -> createSearchResponse(REPOSITORIES.length);
            // many strings, enums and nested messages
            case "large" -> createType();
            // larger API responses
            case "xlarge" -> createSearchResponse(XLARGE_REPOSITORIES);
            case "xxlarge" -> createSearchResponse(XXLARGE_REPOSITORIES);
            default -> throw new IllegalArgumentException(name);
        };
    }

    // owner, owner type, repository, language
    private static final String[][] REPOSITORIES = {
            {"example-org", "Organization", "json-tools", "Java"},
            {"jane-doe", "User", "protobuf-examples", "Kotlin"},
            {"acme", "Organization", "log-encoder", "Go"},
    };

    /**
     * The repositories after the first three are variations of those.
     *
     * @return owner, owner type, repository and language
     */

    private static String[] repository(int i) {
        if (i < REPOSITORIES.length) {
            return REPOSITORIES[i];
        }
        String[] template = REPOSITORIES[i % REPOSITORIES.length];
        return new String[] {template[0] + "-" + i, template[1], template[2] + "-" + i, template[3]};
    }

    /**
     * A search response of the GitHub API, like curioswitch protobuf-jackson uses in its tests.
     *
     * @param repositories number of repositories
     */

    private static SearchResponse createSearchResponse(int repositories) {
        SearchResponse.Builder response = SearchResponse.newBuilder()
                .setTotalCount(1234)
                .setIncompleteResults(false);
        for (int i = 0; i < repositories; i++) {
            String[] repository = repository(i);
            String login = repository[0];
            String name = repository[2];
            String url = "https://api.github.com/repos/" + login + "/" + name;
            String htmlUrl = "https://github.com/" + login + "/" + name;
            long created = 1_300_000_000L + i * 23_456_789L;
            response.addItems(SearchResponse.Item.newBuilder()
                    .setId(10_000_000 + i * 1_234_567)
                    .setName(name)
                    .setFullName(login + "/" + name)
                    .setOwner(createUser(login, 1_000 + i * 4_321, User.Type.valueOf(repository[1])))
                    .setHtmlUrl(htmlUrl)
                    .setDescription("The " + name + " project: tools and examples for " + repository[3] + " developers, with documentation")
                    .setFork(i % 3 == 2)
                    .setUrl(url)
                    .setForksUrl(url + "/forks")
                    .setKeysUrl(url + "/keys{/key_id}")
                    .setCollaboratorsUrl(url + "/collaborators{/collaborator}")
                    .setTeamsUrl(url + "/teams")
                    .setHooksUrl(url + "/hooks")
                    .setIssueEventsUrl(url + "/issues/events{/number}")
                    .setEventsUrl(url + "/events")
                    .setAssigneesUrl(url + "/assignees{/user}")
                    .setBranchesUrl(url + "/branches{/branch}")
                    .setTagsUrl(url + "/tags")
                    .setBlobsUrl(url + "/git/blobs{/sha}")
                    .setGitTagsUrl(url + "/git/tags{/sha}")
                    .setGitRefsUrl(url + "/git/refs{/sha}")
                    .setTreesUrl(url + "/git/trees{/sha}")
                    .setStatusesUrl(url + "/statuses/{sha}")
                    .setLanguagesUrl(url + "/languages")
                    .setStargazersUrl(url + "/stargazers")
                    .setContributorsUrl(url + "/contributors")
                    .setSubscribersUrl(url + "/subscribers")
                    .setSubscriptionUrl(url + "/subscription")
                    .setCommitsUrl(url + "/commits{/sha}")
                    .setGitCommitsUrl(url + "/git/commits{/sha}")
                    .setCommentsUrl(url + "/comments{/number}")
                    .setIssueCommentUrl(url + "/issues/comments{/number}")
                    .setContentsUrl(url + "/contents/{+path}")
                    .setCompareUrl(url + "/compare/{base}...{head}")
                    .setMergesUrl(url + "/merges")
                    .setArchiveUrl(url + "/{archive_format}{/ref}")
                    .setDownloadsUrl(url + "/downloads")
                    .setIssuesUrl(url + "/issues{/number}")
                    .setPullsUrl(url + "/pulls{/number}")
                    .setMilestonesUrl(url + "/milestones{/number}")
                    .setNotificationsUrl(url + "/notifications{?since,all,participating}")
                    .setLabelsUrl(url + "/labels{/name}")
                    .setReleasesUrl(url + "/releases{/id}")
                    .setDeploymentsUrl(url + "/deployments")
                    .setCreatedAt(Timestamp.newBuilder().setSeconds(created))
                    .setUpdatedAt(Timestamp.newBuilder().setSeconds(created + 98_765_432L))
                    .setPushedAt(Timestamp.newBuilder().setSeconds(created + 98_000_000L))
                    .setGitUrl("git://github.com/" + login + "/" + name + ".git")
                    .setSshUrl("git@github.com:" + login + "/" + name + ".git")
                    .setCloneUrl(htmlUrl + ".git")
                    .setSvnUrl(htmlUrl)
                    .setHomepage("https://" + login + ".github.io/" + name)
                    .setSize(1_000 + i * 2_345)
                    .setStargazersCount(100 + i * 321)
                    .setWatchersCount(100 + i * 321)
                    .setLanguage(repository[3])
                    .setHasIssues(true)
                    .setHasProjects(i % 2 == 0)
                    .setHasDownloads(true)
                    .setHasWiki(i % 2 == 1)
                    .setHasPages(i == 0)
                    .setForksCount(10 + i * 7)
                    .setOpenIssuesCount(i * 3)
                    .setForks(10 + i * 7)
                    .setOpenIssues(i * 3)
                    .setWatchers(100 + i * 321)
                    .setDefaultBranch(i % 2 == 0 ? "main" : "master")
                    .setScore(1.0f));
        }
        return response.build();
    }

    private static User createUser(String login, int id, User.Type type) {
        String url = "https://api.github.com/users/" + login;
        return User.newBuilder()
                .setLogin(login)
                .setId(id)
                .setAvatarUrl("https://avatars.githubusercontent.com/u/" + id + "?v=4")
                .setUrl(url)
                .setHtmlUrl("https://github.com/" + login)
                .setFollowersUrl(url + "/followers")
                .setFollowingUrl(url + "/following{/other_user}")
                .setGistsUrl(url + "/gists{/gist_id}")
                .setStarredUrl(url + "/starred{/owner}{/repo}")
                .setSubscriptionsUrl(url + "/subscriptions")
                .setOrganizationsUrl(url + "/orgs")
                .setReposUrl(url + "/repos")
                .setEventsUrl(url + "/events{/privacy}")
                .setReceivedEventsUrl(url + "/received_events")
                .setType(type)
                .build();
    }

    private static com.google.protobuf.Type createType() {
        com.google.protobuf.Type.Builder builder = com.google.protobuf.Type.newBuilder()
                .setName("big.Type")
                .setSyntax(Syntax.SYNTAX_PROTO3)
                .setSourceContext(SourceContext.newBuilder().setFileName("google/protobuf/descriptor.proto"));
        for (FileDescriptor file : List.of(DescriptorProtos.getDescriptor(), StructProto.getDescriptor(), TypeProto.getDescriptor(), ApiProto.getDescriptor())) {
            for (Descriptor descriptor : file.getMessageTypes()) {
                builder.addOneofs(descriptor.getFullName());
                for (FieldDescriptor field : descriptor.getFields()) {
                    builder.addFields(Field.newBuilder()
                            .setName(field.getName())
                            .setJsonName(field.getJsonName())
                            .setNumber(field.getNumber())
                            .setKind(Field.Kind.forNumber(field.getType().toProto().getNumber()))
                            .setCardinality(field.isRepeated() ? Field.Cardinality.CARDINALITY_REPEATED : Field.Cardinality.CARDINALITY_OPTIONAL)
                            .setTypeUrl("type.googleapis.com/" + descriptor.getFullName() + "." + field.getName())
                            .setPacked(field.isPacked()));
                }
            }
        }
        return builder.build();
    }
}
