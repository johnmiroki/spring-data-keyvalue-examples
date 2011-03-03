/*
 * Copyright 2011 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.redis.samples.retwis.redis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Named;

import org.springframework.data.keyvalue.redis.core.BoundHashOperations;
import org.springframework.data.keyvalue.redis.core.StringRedisTemplate;
import org.springframework.data.keyvalue.redis.core.ValueOperations;
import org.springframework.data.keyvalue.redis.support.atomic.RedisAtomicLong;
import org.springframework.data.keyvalue.redis.support.collections.DefaultRedisList;
import org.springframework.data.keyvalue.redis.support.collections.DefaultRedisMap;
import org.springframework.data.keyvalue.redis.support.collections.DefaultRedisSet;
import org.springframework.data.keyvalue.redis.support.collections.RedisList;
import org.springframework.data.keyvalue.redis.support.collections.RedisSet;
import org.springframework.data.redis.samples.Post;
import org.springframework.data.redis.samples.retwis.PostIdGenerator;
import org.springframework.data.redis.samples.retwis.Range;
import org.springframework.data.redis.samples.retwis.RetwisSecurity;
import org.springframework.util.StringUtils;

/**
 * Twitter-clone on top of Redis.
 * 
 * @author Costin Leau
 */
@Named
public class RedisTwitter {

	private static final Pattern MENTION_REGEX = Pattern.compile("@[\\w]+");


	private final StringRedisTemplate template;
	private final ValueOperations<String, String> valueOps;
	// post id generator
	private final PostIdGenerator postIdGenerator;
	// user id generator
	private final RedisAtomicLong userIdCounter;

	// track users
	private RedisList<String> users;
	// time-line
	private final RedisList<String> timeline;

	@Inject
	public RedisTwitter(StringRedisTemplate template) {
		this.template = template;
		valueOps = template.opsForValue();

		users = new DefaultRedisList<String>("users", template);
		timeline = new DefaultRedisList<String>("timeline", template);
		userIdCounter = new RedisAtomicLong("global:uid", template.getConnectionFactory());
		postIdGenerator = new LongGenerator(new RedisAtomicLong("global:pid", template.getConnectionFactory()));
	}

	/**
	 * Adds the given user into the system and fills up the missing links. 
	 *  
	 * @param user
	 * @return
	 */
	public String addUser(String name, String password) {
		String uid = String.valueOf(userIdCounter.incrementAndGet());

		// FIXME: add functionality into the template
		// save user as hash
		// uid -> user
		BoundHashOperations<String, String, String> userOps = template.boundHashOps("uid:" + uid);
		userOps.put("name", name);
		userOps.put("pass", password);

		// link name -> uid
		valueOps.set("user:" + name + ":uid", uid);

		// track name
		users.add(name);

		return addAuth(name);
	}

	public List<Post> getPosts(String uid, Range range) {
		List<String> pids = new DefaultRedisList<String>("posts:" + uid, template).range(range.being, range.end);
		return addReplyLinks(convertPidsToPosts(pids));
	}

	private Post findPost(String pid) {
		Post post = new Post().fromMap(new DefaultRedisMap<String, String>("pid:" + pid, template));
		post.setName(findName(post.getUid()));
		post.setReplyName(findName(post.getReplyUid()));
		post.setPid(pid);
		return post;
	}

	public Set<String> getFollowers(String uid) {
		return covertUidToNames(followers(uid));
	}

	public Set<String> getFollowing(String uid) {
		return covertUidToNames(following(uid));
	}

	public List<Post> getMentions(String uid, Range range) {
		return convertPidsToPosts(mentions(uid).range(range.being, range.end));
	}

	public Collection<Post> timeline(Range range) {
		return addReplyLinks(convertPidsToPosts(timeline.range(range.being, range.end)));
	}

	public Collection<String> newUsers(Range range) {
		return users.range(range.being, range.end);
	}

	public void post(String username, Post post) {

		String uid = findUid(username);
		post.setUid(uid);

		String pid = postIdGenerator.generate();
		post.setPid(pid);

		// add post
		new DefaultRedisMap<String, Object>("pid:" + pid, template).putAll(post.asMap());

		// add links
		new DefaultRedisList<String>("posts:" + uid, template).addFirst(pid);
		timeline.addFirst(pid);

		handleMentions(post);
	}

	private void handleMentions(Post post) {
		// find mentions
		String pid = post.getPid();
		Collection<String> mentions = findMentions(post.getContent());
		for (String mention : mentions) {
			String uid = findUid(mention);
			if (uid != null) {
				mentions(uid).addFirst(pid);
			}
		}
	}

	public String findUid(String name) {
		return valueOps.get("user:" + name + ":uid");
	}

	public boolean isUserValid(String name) {
		return template.hasKey("user:" + name + ":uid");
	}

	private String findName(String uid) {
		BoundHashOperations<String, String, String> userOps = template.boundHashOps("uid:" + uid);
		return userOps.get("name");
	}

	public boolean isAuthValid(String value) {
		String uid = valueOps.get("auth:" + value);
		return (uid != null);
	}

	public boolean auth(String user, String pass) {
		// find uid
		String uid = findUid(user);
		if (StringUtils.hasText(uid)) {
			BoundHashOperations<String, String, String> userOps = template.boundHashOps("uid:" + uid);
			return userOps.get("pass").equals(pass);
		}

		return false;
	}

	public String findNameForAuth(String value) {
		String uid = valueOps.get("auth:" + value);
		return findName(uid);
	}

	/**
	 * Adds auth key to Redis for the given name.
	 * 
	 * @param uid
	 * @return
	 */
	public String addAuth(String name) {
		String uid = findUid(name);
		// add random auth key relation
		String auth = UUID.randomUUID().toString();
		valueOps.set("uid:" + uid + ":auth", auth);
		valueOps.set("auth:" + auth, uid);
		return auth;
	}

	public void deleteAuth(String user) {
		String uid = findUid(user);

		String authKey = "uid:" + uid + ":auth";
		String auth = valueOps.get(authKey);

		template.delete(Arrays.asList(authKey, "auth:" + auth));
	}

	public boolean isFollowing(String uid, String targetUid) {
		return following(uid).contains(targetUid);
	}

	public void follow(String targetUser) {
		String targetUid = findUid(targetUser);

		following(RetwisSecurity.getUid()).add(targetUid);
		followers(targetUid).add(RetwisSecurity.getUid());
	}

	public void stopFollowing(String targetUser) {
		String targetUid = findUid(targetUser);

		following(RetwisSecurity.getUid()).remove(targetUid);
		followers(targetUid).remove(RetwisSecurity.getUid());
	}

	public Set<String> alsoFollowed(String uid, String targetUid) {
		return covertUidToNames(following(uid).intersect(followers(targetUid)));
	}

	public Set<String> commonFollowers(String uid, String targetUid) {
		return covertUidToNames(following(uid).intersect(following(targetUid)));
	}

	private RedisSet<String> following(String uid) {
		return new DefaultRedisSet<String>("uid:" + uid + ":following", template);
	}

	private RedisSet<String> followers(String uid) {
		return new DefaultRedisSet<String>("uid:" + uid + ":followers", template);
	}

	private RedisList<String> mentions(String uid) {
		return new DefaultRedisList<String>("uid:" + uid + ":mentions", template);
	}

	private Set<String> covertUidToNames(Set<String> uids) {
		Set<String> set = new LinkedHashSet<String>(uids.size());

		for (String uid : uids) {
			set.add(findName(uid));
		}
		return set;
	}

	private List<Post> convertPidsToPosts(Collection<String> pids) {
		List<Post> posts = new ArrayList<Post>(pids.size());

		//FIXME: add basic mapping mechanism
		//FIXME: optimize this N+1
		for (String pid : pids) {
			posts.add(findPost(pid));
		}

		return posts;
	}

	private List<Post> addReplyLinks(List<Post> posts) {
		for (Post post : posts) {
			String content = post.getContent();
			Matcher regexMatcher = MENTION_REGEX.matcher(content);
			boolean replace = false;

			while (regexMatcher.find()) {
				replace = true;
				String match = regexMatcher.group();
				int start = regexMatcher.start();
				int stop = regexMatcher.end();


				content = content.substring(0, start) + "<a href=\"!" + match.substring(1) + "\">" + match + "</a>"
						+ content.substring(stop);
			}
			if (replace) {
				post.setContent(content);
			}
		}
		return posts;
	}


	public static Collection<String> findMentions(String content) {
		Matcher regexMatcher = MENTION_REGEX.matcher(content);
		List<String> mentions = new ArrayList<String>(4);

		while (regexMatcher.find()) {
			mentions.add(regexMatcher.group().substring(1));
		}

		return mentions;
	}
}