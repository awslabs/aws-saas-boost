/**
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.amazon.aws.partners.saasfactory.controller;

import org.apache.commons.codec.binary.Hex;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.security.SecureRandom;
import java.util.*;

@Controller
public class LoadController {

    private static final int CPU_LOAD_LOOP = 100000;

    @GetMapping("/load.html")
    public String cpuLoad(Map<String, Object> model) {
        long startTimeMillis = System.currentTimeMillis();
        String encrypted = encrypt(THE_RAVEN);
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;

        model.put("executionTime", (totalTimeMillis / 1000));
        model.put("encrypted", encrypted);
        model.put("text", THE_RAVEN.replaceAll("\\n", "<br/>"));

        return "load";
    }

    public static String encrypt(String message) {
        String secret = null;
        try {
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(128);
            SecretKey secretKey = generator.generateKey();

            //Encrypt
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);

            // Artificially loop to generate CPU load
            for (int i = 0; i < CPU_LOAD_LOOP; i++) {
                cipher.update(message.getBytes());
            }

            byte[] data = cipher.doFinal();
            secret = Hex.encodeHexString(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return secret;
    }

    @GetMapping("/sort.html")
    @ResponseBody
    public String getSort(@RequestParam String count) {


        int inCount = Integer.valueOf(count);
        long startTimeMillis = System.currentTimeMillis();
        String tenantId = System.getenv("TENANT_ID");
        if (tenantId == null || tenantId.isEmpty()) {
            tenantId = "Unknown";
        }
        HashMap<Integer, String> map = new HashMap<Integer, String>();
        for (int i=0 ; i < inCount; i++) {
            map.put(i, "THIS IS A LONG STRING THAT WE NEED TO SORT " + i);
        }

        List<String> strings = new ArrayList<String>();
        strings.addAll(map.values());
        Collections.shuffle(strings);
        Collections.sort(strings);;

        long milli = System.currentTimeMillis() - startTimeMillis;
        return "Hello Tenant V2 Test " + tenantId + ", count " + count + " in " + milli;
    }

    public static final String THE_RAVEN = "Once upon a midnight dreary, while I pondered, weak and weary,\n" +
            "Over many a quaint and curious volume of forgotten lore,\n" +
            "While I nodded, nearly napping, suddenly there came a tapping,\n" +
            "As of some one gently rapping, rapping at my chamber door.\n" +
            "\"‘Tis some visiter,\" I muttered, \"tapping at my chamber door—\n" +
            "                         Only this, and nothing more.\"\n" +
            "\n" +
            "Ah, distinctly I remember it was in the bleak December,\n" +
            "And each separate dying ember wrought its ghost upon the floor.\n" +
            "Eagerly I wished the morrow;—vainly I had sought to borrow\n" +
            "From my books surcease of sorrow—sorrow for the lost Lenore—\n" +
            "For the rare and radiant maiden whom the angels name Lenore—\n" +
            "                         Nameless here for evermore.\n" +
            "\n" +
            "And the silken sad uncertain rustling of each purple curtain\n" +
            "Thrilled me—filled me with fantastic terrors never felt before;\n" +
            "So that now, to still the beating of my heart, I stood repeating\n" +
            "\"‘Tis some visiter entreating entrance at my chamber door—\n" +
            "Some late visiter entreating entrance at my chamber door;—\n" +
            "                         This it is, and nothing more.\"\n" +
            "\n" +
            "Presently my soul grew stronger; hesitating then no longer,\n" +
            "\"Sir,\" said I, \"or Madam, truly your forgiveness I implore;\n" +
            "But the fact is I was napping, and so gently you came rapping,\n" +
            "And so faintly you came tapping, tapping at my chamber door,\n" +
            "That I scarce was sure I heard you \"—here I opened wide the door;——\n" +
            "                         Darkness there and nothing more.\n" +
            "\n" +
            "Deep into that darkness peering, long I stood there wondering, fearing,\n" +
            "Doubting, dreaming dreams no mortal ever dared to dream before;\n" +
            "But the silence was unbroken, and the darkness gave no token,\n" +
            "And the only word there spoken was the whispered word, \"Lenore!\"\n" +
            " This I whispered, and an echo murmured back the word, \"Lenore!\"—\n" +
            "                         Merely this, and nothing more.\n" +
            "\n" +
            "Back into the chamber turning, all my soul within me burning,\n" +
            "Soon I heard again a tapping somewhat louder than before.\n" +
            "\"Surely,\" said I, \"surely that is something at my window lattice;\n" +
            "Let me see, then, what thereat is, and this mystery explore—\n" +
            "Let my heart be still a moment and this mystery explore;—\n" +
            "                         ‘Tis the wind and nothing more!\"\n" +
            "\n" +
            "Open here I flung the shutter, when, with many a flirt and flutter,\n" +
            "In there stepped a stately raven of the saintly days of yore;\n" +
            "Not the least obeisance made he; not an instant stopped or stayed he;\n" +
            "But, with mien of lord or lady, perched above my chamber door—\n" +
            "Perched upon a bust of Pallas just above my chamber door—\n" +
            "                         Perched, and sat, and nothing more.\n" +
            "\n" +
            "Then this ebony bird beguiling my sad fancy into smiling,\n" +
            "By the grave and stern decorum of the countenance it wore,\n" +
            "\"Though thy crest be shorn and shaven, thou,\" I said, \"art sure no craven,\n" +
            "Ghastly grim and ancient raven wandering from the Nightly shore—\n" +
            "Tell me what thy lordly name is on the Night’s Plutonian shore!\"\n" +
            "                         Quoth the raven \"Nevermore.\"\n" +
            "\n" +
            "Much I marvelled this ungainly fowl to hear discourse so plainly,\n" +
            "Though its answer little meaning—little relevancy bore;\n" +
            "For we cannot help agreeing that no living human being\n" +
            "Ever yet was blessed with seeing bird above his chamber door—\n" +
            "Bird or beast upon the sculptured bust above his chamber door,\n" +
            "                        With such name as \"Nevermore.\"\n" +
            "\n" +
            "But the raven, sitting lonely on the placid bust, spoke only\n" +
            "That one word, as if his soul in that one word he did outpour.\n" +
            "Nothing farther then he uttered—not a feather then he fluttered—\n" +
            "Till I scarcely more than muttered \"Other friends have flown before—\n" +
            "On the morrow he will leave me, as my hopes have flown before.\"\n" +
            "                         Then the bird said \"Nevermore.\"\n" +
            "\n" +
            "Startled at the stillness broken by reply so aptly spoken,\n" +
            "\"Doubtless,\" said I, \"what it utters is its only stock and store\n" +
            "Caught from some unhappy master whom unmerciful Disaster\n" +
            "Followed fast and followed faster till his songs one burden bore—\n" +
            "Till the dirges of his Hope that melancholy burden bore\n" +
            "                        Of \"Never—nevermore.\"\n" +
            "\n" +
            "But the raven still beguiling all my sad soul into smiling,\n" +
            "Straight I wheeled a cushioned seat in front of bird, and bust and door;\n" +
            "Then, upon the velvet sinking, I betook myself to thinking\n" +
            "Fancy unto fancy, thinking what this ominous bird of yore—\n" +
            "What this grim, ungainly, ghastly, gaunt and ominous bird of yore\n" +
            "                        Meant in croaking \"Nevermore.\"\n" +
            "\n" +
            "This I sat engaged in guessing, but no syllable expressing\n" +
            "To the fowl whose fiery eyes now burned into my bosom’s core;\n" +
            "This and more I sat divining, with my head at ease reclining\n" +
            "On the cushion’s velvet lining that the lamplght gloated o’er,\n" +
            "But whose velvet violet lining with the lamplight gloating o’er,\n" +
            "                         She shall press, ah, nevermore!\n" +
            "\n" +
            "Then, methought, the air grew denser, perfumed from an unseen censer\n" +
            "Swung by Angels whose faint foot-falls tinkled on the tufted floor.\n" +
            "\"Wretch,\" I cried, \"thy God hath lent thee—by these angels he hath sent\n" +
            "thee\n" +
            "Respite—respite and nepenthe from thy memories of Lenore;\n" +
            "Quaff, oh quaff this kind nepenthe and forget this lost Lenore!\"\n" +
            "                          Quoth the raven, \"Nevermore.\"\n" +
            "\n" +
            "\"Prophet!\" said I, \"thing of evil!—prophet still, if bird or devil!—\n" +
            "Whether Tempter sent, or whether tempest tossed thee here ashore,\n" +
            "Desolate yet all undaunted, on this desert land enchanted—\n" +
            "On this home by Horror haunted—tell me truly, I implore—\n" +
            "Is there—is there balm in Gilead?—tell me—tell me, I implore!\"\n" +
            "                          Quoth the raven, \"Nevermore.\"\n" +
            "\n" +
            "\"Prophet!\" said I, \"thing of evil—prophet still, if bird or devil!\n" +
            "By that Heaven that bends above us—by that God we both adore—\n" +
            "Tell this soul with sorrow laden if, within the distant Aidenn,\n" +
            "It shall clasp a sainted maiden whom the angels name Lenore—\n" +
            "Clasp a rare and radiant maiden whom the angels name Lenore.\"\n" +
            "                          Quoth the raven, \"Nevermore.\"\n" +
            "\n" +
            "\"Be that word our sign of parting, bird or fiend!\" I shrieked, upstarting—\n" +
            "\"Get thee back into the tempest and the Night’s Plutonian shore!\n" +
            "Leave no black plume as a token of that lie thy soul hath spoken!\n" +
            "Leave my loneliness unbroken!—quit the bust above my door!\n" +
            "Take thy beak from out my heart, and take thy form from off my door!\"\n" +
            "                         Quoth the raven, \"Nevermore.\"\n" +
            "\n" +
            "And the raven, never flitting, still is sitting, still is sitting\n" +
            "On the pallid bust of Pallas just above my chamber door;\n" +
            "And his eyes have all the seeming of a demon’s that is dreaming,\n" +
            "And the lamp-light o’er him streaming throws his shadow on the floor;\n" +
            "And my soul from out that shadow that lies floating on the floor\n" +
            "                         Shall be lifted—nevermore!";
}
