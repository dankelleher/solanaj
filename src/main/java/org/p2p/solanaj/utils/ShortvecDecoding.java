package org.p2p.solanaj.utils;

import java.io.ByteArrayInputStream;

public class ShortvecDecoding {

    /*
    let len = 0;
  let size = 0;
  for (;;) {
    let elem = bytes.shift() as number;
    len |= (elem & 0x7f) << (size * 7);
    size += 1;
    if ((elem & 0x80) === 0) {
      break;
    }
  }
  return len;
     */
    public static int decodeLength(ByteArrayInputStream bytes) {
        int len = 0;
        int size = 0;

        for (;;) {
            int elem = bytes.read();
            len |= (elem & 0x7f) << (size * 7);
            size += 1;

            if ((elem & 0x80) == 0) {
                break;
            }
        }

        return len;
    }

}
